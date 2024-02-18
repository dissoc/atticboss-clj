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

(ns cloboss.transactions.jdbc-test
  (:require [clojure.test :refer :all]
            [cloboss.transactions :refer (transaction)]
            [cloboss.transactions.jdbc :refer (factory)]
            [cloboss.util :refer [in-container? set-log-level!]]
            [clojure.java.jdbc :as sql]))

(set-log-level! (or (System/getenv "LOG_LEVEL") :OFF))

(if (in-container?)

  (let [spec {:factory factory :name "java:jboss/datasources/ExampleDS"}]
    (deftest jdbc-transactions
      (sql/db-do-commands spec
        (sql/create-table-ddl :things [[:name :varchar]]))

      (transaction
        (sql/insert! spec :things {:name "success"}))

      (transaction
        (sql/with-db-transaction [con spec]
          (sql/delete! con :things [true])
          (sql/db-set-rollback-only! con)))

      (try
        (transaction
          (sql/delete! spec :things [true])
          (throw (NegativeArraySizeException. "test rollback by exception")))
        (catch NegativeArraySizeException _))

      (is (= "success" (-> (sql/query spec ["select name from things"])
                         first
                         :name)))))

  (deftest factory-delegation
    (with-open [c (sql/get-connection {:factory factory
                                       :connection-uri "jdbc:h2:mem:ooc"})
                s (.prepareStatement c "")]
      (.clearParameters s)
      (is (= c (.getConnection s)))
      (is (not (.isClosed c)))
      (is (not (.isClosed s)))
      (.close c)
      (is (.isClosed c)))))
