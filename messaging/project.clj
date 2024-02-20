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

(defproject org.cloboss/messaging "2.2.0-SNAPSHOT"
  :description "Easily publish and receive messages containing any type of nested data structure to dynamically-created queues and topics."
  :plugins [[lein-modules "0.3.11"]]

  :dependencies [[org.cloboss/core "2.2.0-SNAPSHOT"]
                 [top.atticboss/atticboss-messaging-artemis "0.14.0-SNAPSHOT"]]

  :jvm-opts ["-Dhornetq.data.dir=target/hornetq-data"]

  :profiles {:dev
             {:dependencies [[cheshire _]
                             [org.clojure/data.fressian _]]}})
