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

(defproject org.cloboss/cloboss "2.2.0-SNAPSHOT"
  :description "A catch-all pom that brings in all Immutant libs."
  :plugins [[lein-modules "0.3.11"]]
  :packaging "pom"

  :dependencies [[org.cloboss/core "2.2.0-SNAPSHOT"]
                 [org.cloboss/caching "2.2.0-SNAPSHOT"]
                 [org.cloboss/messaging "2.2.0-SNAPSHOT"]
                 [org.cloboss/scheduling "2.2.0-SNAPSHOT"]
                 [org.cloboss/web "2.2.0-SNAPSHOT"]
                 [org.cloboss/transactions "2.2.0-SNAPSHOT"]
                 [top.atticboss/atticboss-messaging-artemis "0.14.0-SNAPSHOT"]])