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

(ns ^:no-doc ^:internal cloboss.internal.options
  "Functions for validating options."
  (:import [org.projectodd.atticboss Option]))

(defn ->var [x]
  (if (var? x) x (resolve x)))

(defmacro set-valid-options! [v opts]
  (let [v# (->var v)]
    `(alter-meta! ~v# assoc :valid-options ~opts)))

(defmacro valid-options-for [src]
  (let [src# (->var src)]
    `(-> ~src# meta :valid-options)))

(defn validate-options*
  [opts valid-keys name]
  (if (::validated? opts)
    opts
    (do
      (doseq [k (keys opts)]
        (if-not (valid-keys k)
          (throw (IllegalArgumentException.
                   (format "%s is not a valid option for %s, valid options are: %s"
                     k name valid-keys)))))
      (assoc opts ::validated? true))))

(defmacro validate-options
  "Validates that (keys opts) is a subset of :valid-options from (meta src)"
  ([opts src]
     `(validate-options ~opts ~src ~src))
  ([opts src alt-name]
     (let [src-var# (->var src)]
       `(validate-options* ~opts
          (valid-options-for ~src-var#)
          (name (quote ~alt-name))))))

(defmacro concat-valid-options
  "Grabs the :valid-options metadata from all of the passed vars, and concats them together into a set."
  [& vars]
  (let [vars# (mapv ->var vars)]
    `(set (mapcat valid-options-for ~vars#))))

(defn keywordize [^String v]
  (keyword (.replace v \_ \-)))

(defn ->underscored-string [v]
  (when v
    (-> v
      name
      (.replace \- \_)
      (.replace "?" ""))))

(defn ^java.util.Map opts->map
  "Converts an Option class into a map of name -> Option instance."
  [^Class class]
  ;; clojure 1.7.0 no longer initializes classes on import, so we have
  ;; to force init here (see CLJ-1315)
  (Class/forName (.getName class))
  (->> class
    Option/optsFor
    (map (fn [^Option o] (vector (.name o) o)))
    (into {})))

(defn opts->defaults-map
  "Converts an Option class into a map of name as keyword -> default value."
  [class]
  (->> class
    opts->map
    (map (fn [[k ^Option v]] [(keywordize k) (.defaultValue v)]))
    (into {})))

(defn opts->keywords
  "Converts an Option class into a list of names for those Options as keywords.
   Auto-converts \"foo_bar\" to :foo-bar."
  [class]
  (->> class opts->map keys (map keywordize)))

(defn opts->set
  "Converts an Option classes into a set of keywords."
  [& classes]
  (->> classes (mapcat opts->keywords) set))

(defn ^java.util.Map extract-options
  "Converts a clojure map into a AtticBoss options map."
  [m c]
  (let [optsm (opts->map c)]
    (->> m
      (map (fn [[k v]] (if (keyword? k) [(name k) v] [k v])))
      (map (fn [[k v]]
             (when-let [key (optsm k
                              (optsm (->underscored-string k)))]
               [key v])))
      (into {}))))

(defn set-properties
  "TODO: remove the stringification in here prior to the next
  non-patch release and doc as a breaking change

  If :properties appears in the options, use it, otherwise use the
  passed metadata.

  Also known as 'fixing your broken cancer', we allow you to pass
  properties/metadata with keyword keys, but we stringify them for you
  so that our probably-wrong-but-possibly-relied-upon tests continue
  to pass"
  [options metadata]
  (->> (or (:properties options) metadata)
    (map (fn [[k v]] (if (keyword? k) [(name k) v] [k v])))
    (into {})
    (assoc options :properties)))

(defn boolify
  "Appends ? to each of `keywords` in `coll`, replacing the original.

   `coll` must be a set or map."
  [coll & keywords]
  (letfn [(add-? [kw]
             (keyword (str (name kw) \?)))]
    (reduce
      (if (set? coll)
        (fn [s kw]
          (conj (disj s kw) (add-? kw)))
        (fn [m kw]
          (let [currval (kw m)]
            (assoc (dissoc m kw)
              (add-? kw) currval))))
      coll
      keywords)))

(defn coerce
  "Handy when values might come in as strings, e.g. via `lein run`,
  but shouldn't be. Takes a sequence of keys and the map"
  [ks m]
  (merge m
    (reduce
      (fn [m [k v]] (assoc m k (if (string? v) (read-string v) v)))
      {}
      (select-keys m ks))))
