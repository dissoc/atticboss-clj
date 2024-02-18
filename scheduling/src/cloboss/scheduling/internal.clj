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

(ns ^:no-doc ^:internal cloboss.scheduling.internal
    (:require [cloboss.internal.options :as o]
              [cloboss.internal.util    :as u])
  (:import org.projectodd.atticboss.AtticBoss
           [org.projectodd.atticboss.scheduling
            Scheduling Scheduling$CreateOption Scheduling$ScheduleOption]))

(def ^:internal create-defaults (o/opts->defaults-map Scheduling$CreateOption))
(def ^:internal schedule-defaults
  (-> (o/opts->defaults-map Scheduling$ScheduleOption)
    (o/boolify :allow-concurrent-exec)
    (assoc :singleton false)))

(def scheduler-name
  (partial u/hash-based-component-name create-defaults))

(defn ^Scheduling scheduler [opts]
  (AtticBoss/findOrCreateComponent Scheduling
    (scheduler-name (select-keys opts (o/valid-options-for scheduler)))
    (o/extract-options opts Scheduling$CreateOption)))

(o/set-valid-options! scheduler (o/opts->set Scheduling$CreateOption))
