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

(ns cloboss.scheduling-test
  (:require [clojure.test                 :refer :all]
            [cloboss.scheduling          :refer :all]
            [cloboss.scheduling.internal :refer :all]
            [cloboss.util                :as u])
  (:import [java.util.concurrent CountDownLatch TimeUnit]))

(u/set-log-level! (or (System/getenv "LOG_LEVEL") :OFF))

(use-fixtures :each
  u/reset-fixture
  #(let [s (scheduler {})]
     (doseq [x (.scheduledJobs s)]
       (stop {:id x}))
     (%)))

(deftest scheduling-should-work
  (let [p (promise)]
    (schedule #(deliver p :success) {})
    (is (= :success (deref p 10000 :failure)))))

(deftest scheduling-should-take-kwargs
  (let [p (promise)]
    (schedule #(deliver p :success) :limit 1 :every 1)
    (is (= :success (deref p 10000 :failure)))))

(deftest should-return-opts-with-the-defaults
  (let [result (schedule #() (in 1))]
    (is (= (-> (merge create-defaults schedule-defaults) keys (conj :id :ids) set)
          (-> result keys set)))
    (is (:id result))
    (is (:ids result))))

(deftest should-return-given-opts-not-overridden-by-defaults
  (let [opts {:id :foo :num-threads 1}
        result (schedule #() opts)]
    (is (= (-> (merge create-defaults schedule-defaults) keys (conj :id :ids) set)
          (-> result keys set)))
    (is (= opts (select-keys result (keys opts))))))

(deftest stop-should-work
    (let [started? (promise)
          should-run? (atom true)
          env (schedule
                (fn []
                  (is @should-run?)
                  (deliver started? true))
                (-> (every 100)
                  (limit 5)))]
      (is (deref started? 5000 false))
      (is (stop env))
      (is (not (stop env)))))

(deftest stop-should-take-kwargs
  (let [started? (promise)
        should-run? (atom true)]
    (schedule
      (fn []
        (is @should-run?)
        (deliver started? true))
      (-> (id :foo)
        (every 10)
        (limit 5000)))
    (is (deref started? 5000 false))
    (is (stop :id :foo))))

(deftest stop-should-stop-the-scheduler-when-no-jobs-remain
  (let [job1 (schedule #() :every :second)
        job2 (schedule #() :every :second)
        scheduler (.scheduler (scheduler {}))]
    (is (not (.isShutdown scheduler)))
    (stop job1)
    (is (not (.isShutdown scheduler)))
    (stop job2)
    (is (.isShutdown scheduler))))

(deftest stop-should-stop-all-jobs-and-the-scheduler-when-given-no-args
  (let [job (schedule #() :every :second)
        scheduler (.scheduler (scheduler {}))]
    (is (not (.isShutdown scheduler)))
    (stop)
    (is (.isShutdown scheduler))))

(deftest stop-should-stop-all-threaded-jobs
  (let [everything (-> (schedule #() :every :second)
                     (dissoc :id)
                     (->> (schedule #()))
                     (dissoc :id)
                     (->> (schedule #())))
        scheduler (scheduler {})]
    (is (true? (stop everything)))
    (is (empty? (.scheduledJobs scheduler)))
    (is (.isShutdown (.scheduler scheduler)))
    (is (not (stop everything)))))

(deftest multiple-schedulers
  (let [default (doto (scheduler {}) .start)
        other-scheduler (doto (scheduler {:num-threads 1}) .start)]
    (is (not= other-scheduler default))
    (is (not= (.scheduler other-scheduler) (.scheduler default)))))

(defn run-with-maybe-concurrent-exec [concurrent? sleep]
  (let [latch (CountDownLatch. 5)
        ts    (atom [])
        job   (schedule (fn []
                          (swap! ts conj (System/currentTimeMillis))
                          (Thread/sleep sleep)
                          (.countDown latch))
                :every 10
                :allow-concurrent-exec? concurrent?)]
    (is (.await latch 10 TimeUnit/SECONDS))
    (stop job)
    (->> @ts reverse (partition 2 1) (map #(apply - %)))))

(let [sleep-duration 50]
  (deftest concurrent-execution-enabled
    (is (some #(< % sleep-duration) (run-with-maybe-concurrent-exec true sleep-duration))))

  (deftest concurrent-execution-disabled
    (doseq [delta (run-with-maybe-concurrent-exec false sleep-duration)]
      (is (>= delta sleep-duration)))))
