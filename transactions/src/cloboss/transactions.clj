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

(ns cloboss.transactions
  "Provides support for distributed (XA) transactions."
  (:import [org.projectodd.atticboss AtticBoss]
           [org.projectodd.atticboss.transactions Transaction]))

(def ^:no-doc ^Transaction tx (memoize #(AtticBoss/findOrCreateComponent Transaction)))

(defn ^javax.transaction.TransactionManager manager
  "The JTA TransactionManager"
  []
  (.manager (tx)))

(defmacro transaction
  "Execute body within current transaction, if any, otherwise start a
  new transaction, execute body, and either commit or rollback all
  actions on XA components contained therein atomically. Any exception
  tossed within the body will cause the transaction to rollback.
  Otherwise, the transaction is committed and the value of the last
  expression in the body is returned. This is effectively an alias for
  the [[cloboss.transactions.scope/required]] transaction scope."
  [& body]
  (let [f `(fn [] ~@body)]
    `(.required (tx) ~f)))

(defn set-rollback-only
  "Modify the current transaction such that the only possible outcome
  is a rollback; useful when rollback is desired but an exception is
  not"
  []
  (.setRollbackOnly (manager)))

(defn enlist
  "Enlist a valid XAResource as a participant in the current
  transaction. Not required for Immutant resources, i.e. messaging and
  caching, as they will be enlisted automatically."
  [^javax.transaction.xa.XAResource resource]
  (-> (manager)
    .getTransaction
    (.enlistResource resource)))
