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

(ns cloboss.codecs-test
  (:require [cloboss.codecs :refer :all]
            [clojure.test    :refer :all]))

(deftest codec-set-should-work
  (is (= #{:none :edn :json} (codec-set))))

(defn test-codec [object encoding]
  (let [encoded (encode object encoding)]
    (is (= object (decode encoded encoding)))))

(deftest encode-string
  (doseq [e (codec-set)]
    (test-codec "a random text message" e)))

(deftest edn-date
  (test-codec (java.util.Date.) :edn))

(deftest edn-date-inside-hash
  (test-codec {:date (java.util.Date.)} :edn))

(deftest edn-date-inside-vector
  (test-codec [(java.util.Date.)] :edn))

(deftest complex-hash
  (doseq [e (codec-set)]
    (test-codec {:a "b" :c [1 2 3 {:foo 42}]} e)))

(deftest complex-json-encoding
  (let [message {:a "b" :c [1 2 3 {:foo 42}]}
        encoded (encode message :json)]
    (is (= message (decode encoded :json)))
    (is (= message (decode "{\"a\":\"b\",\"c\":[1,2,3,{\"foo\":42}]}" :json)))))

(deftest decode-nil
  (doseq [codec-name (codec-set)]
    (let [codec (lookup-codec codec-name)]
      (is (nil? (.decode codec (.encode codec nil)))))))

(defrecord ARecord [x])

(deftest records-via-none-encoding
  (test-codec (->ARecord :x) :none))

(deftest edn-decode-errors-should-throw-ex-info
  (try
    (decode "(" :edn)
    (is false)
    (catch clojure.lang.ExceptionInfo e
      (is (= {:data "("
              :type :decode-exception}
            (ex-data e))))))

(deftest json-decode-errors-should-throw-ex-info
  (try
    (decode "[" :json)
    (is false)
    (catch clojure.lang.ExceptionInfo e
      (is (= {:data "["
              :type :decode-exception}
            (ex-data e))))))
