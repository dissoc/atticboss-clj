;; Copyright 2014-2017 Red Hat, Inc, and individual contributors.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns cloboss.transactions-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [cloboss.transactions :refer :all]
            [cloboss.transactions.scope :refer (required not-supported)]
            [cloboss.util :refer [in-container? in-eap? set-log-level! messaging-remoting-port]]
            [cloboss.messaging :as msg]
            [cloboss.caching   :as csh]
            [clojure.java.jdbc :as sql]))

(set-log-level! (or (System/getenv "LOG_LEVEL") :OFF))
(def cache (csh/cache "tx-test" :transactional? true))
(def queue (msg/queue "/queue/test" :durable? false))
(def local-remote-queue (msg/queue "remote" :durable? false))
(def conn (delay (msg/context (cond-> {:host "localhost" :xa? true}
                                (in-container?) (assoc :port (messaging-remoting-port)
                                                  :username "testuser" :password "testuser1!"
                                                  :remote-type :hornetq-wildfly)
                                (in-eap?) (dissoc :remote-type)))))
(def remote-queue (delay (msg/queue "remote" :context @conn)))
(def trigger (msg/queue "/queue/trigger" :durable? false))
(def spec {:connection-uri "jdbc:h2:mem:ooc;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"})

(use-fixtures :each
  (fn [f]
    (.clear cache)
    (try
      (sql/db-do-commands spec
        [(sql/drop-table-ddl :things)
         (sql/create-table-ddl :things [[:name "varchar(50)"]])])
      (catch Exception _))
    (f)))

(use-fixtures :once
  (fn [f]
    (f)
    (.close @conn)))

;;; Helper methods to verify database activity
(defn write-thing-to-db [spec name]
  (sql/insert! spec :things {:name name}))
(defn read-thing-from-db [spec name]
  (-> (sql/query spec ["select name from things where name = ?" name])
    first))
(defn count-things-in-db [spec]
  (-> (sql/query spec ["select count(*) c from things"])
    first
    :c
    int))

(defn work [m]
  (csh/swap-in! cache :a (constantly 1))
  (msg/publish queue "kiwi")
  (msg/publish @remote-queue "starfruit")
  (not-supported
    (csh/swap-in! cache :deliveries (fnil inc 0)))
  (sql/with-db-transaction [t spec]
    (write-thing-to-db t "tangerine")
    (when (:throw? m) (throw (Exception. "rollback")))
    (when (:rollback? m)
      (set-rollback-only)
      (sql/db-set-rollback-only! t))))

(defn listener [m]
  (if (:tx? m)
    (transaction (work m))
    (work m)))

(defn attempt-transaction-external [& {:as m}]
  (try
    (with-open [conn (msg/context :xa? true)]
      (transaction
        (msg/publish queue "pineapple" :context conn)
        (work m)))
    (catch Exception e
      (-> e .getMessage))))

(defn attempt-transaction-internal [& {:as m}]
  (try
    (transaction
      (work m))
    (catch Exception e
      (-> e .getMessage))))

(defn verify-success []
  (is (= "kiwi" (msg/receive queue :timeout 1000)))
  (is (= "starfruit" (msg/receive local-remote-queue :timeout 1000)))
  (is (= 1 (:a cache)))
  (is (= "tangerine" (:name (read-thing-from-db spec "tangerine"))))
  (is (= 1 (count-things-in-db spec))))

(defn verify-failure []
  (is (nil? (msg/receive queue :timeout 1000)))
  (is (nil? (msg/receive local-remote-queue :timeout 1000)))
  (is (nil? (:a cache)))
  (is (nil? (read-thing-from-db spec "tangerine")))
  (is (= 0 (count-things-in-db spec))))

(deftest verify-transaction-success-external
  (is (nil? (attempt-transaction-external)))
  (is (= "pineapple" (msg/receive queue :timeout 1000)))
  (verify-success))

(deftest verify-transaction-failure-external
  (is (= "rollback" (attempt-transaction-external :throw? true)))
  (verify-failure))

(deftest verify-transaction-success-internal
  (is (nil? (attempt-transaction-internal)))
  (verify-success))

(deftest verify-transaction-failure-internal
  (is (= "rollback" (attempt-transaction-internal :throw? true)))
  (verify-failure))

(deftest transactional-receive
  (msg/publish queue "foo")
  (required
    (msg/receive queue)
    (set-rollback-only))
  (is (= "foo" (msg/receive queue :timeout 1000))))

(deftest transactional-writes-in-listener-should-work
  (with-open [_ (msg/listen trigger listener)]
    (msg/publish trigger {:tx? true})
    (verify-success)))

(deftest transactional-writes-in-listener-should-fail-on-exception
  (with-open [_ (msg/listen trigger listener)]
    (msg/publish trigger {:tx? true :throw? true})
    (verify-failure)
    (is (= 10 (:deliveries cache)))))

(deftest transactional-writes-in-listener-should-fail-on-rollback
  (with-open [_ (msg/listen trigger listener)]
    (msg/publish trigger {:tx? true :rollback? true})
    (verify-failure)
    (is (= 1 (:deliveries cache)))))

(deftest non-transactional-writes-in-listener-with-exception
  (with-open [_ (msg/listen trigger listener :mode :auto-ack)]
    (msg/publish trigger {:throw? true})
    (is (= 10 (loop [i 0]
                (Thread/sleep 100)
                (if (or (= 50 i) (= 10 (:deliveries cache)))
                  (:deliveries cache)
                  (recur (inc i))))))
    (is (= 1 (:a cache)))
    (is (= (repeat 10 "kiwi")      (repeatedly 10 #(msg/receive queue))))
    (is (= (repeat 10 "starfruit") (repeatedly 10 #(msg/receive local-remote-queue))))))

(deftest remote-xa-listen-should-work
  (msg/queue "remote-xa-listen" :durable? false)
  (let [extra-connect-opts
        (cond-> []
          (in-container?) (conj :username "testuser" :password "testuser1!")
          (and (in-container?) (not (in-eap?))) (conj :remote-type :hornetq-wildfly))]
    (with-open [c (apply msg/context :host "localhost" :port (messaging-remoting-port) :xa? true
                    extra-connect-opts)]
      (let [q (msg/queue "remote-xa-listen" :context c)
            p (promise)]
        (with-open [listener (msg/listen q (partial deliver p))]
          (msg/publish q :hi)
          (is (= :hi (deref p 60000 :fail))))))))
