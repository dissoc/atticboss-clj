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

(ns testing.app
  (:require [clojure.java.io :as io]
            [cloboss.web :as web]
            [cloboss.web.async :as async]
            [cloboss.web.sse :as sse]
            [cloboss.web.internal.servlet :as servlet]
            [cloboss.web.internal.ring    :as ring]
            [cloboss.internal.util :refer [maybe-deref]]
            [cloboss.web.middleware :refer (wrap-session wrap-websocket)]
            [cloboss.codecs :refer (encode)]
            [compojure.core :refer (ANY GET defroutes)]
            [ring.util.response :refer (charset redirect response)]
            [ring.middleware.params :refer [wrap-params]])
  (:import [jakarta.servlet.http HttpServlet]))

(defn counter [{:keys [session websocket?] :as request}]
  (if websocket?
    ;; just to test websockets at /, has nothing to do with the session
    (async/as-channel request
      :on-message (fn [ch message]
                    (async/send! ch (str "ROOT" message))))
    (let [count (:count session 0)
          session (assoc session :count (inc count))]
      (-> (response (str count))
        (assoc :session session)))))

(defn dump
  [request]
  (let [data (encode (dissoc request :server-exchange :body :servlet
                       :servlet-request :servlet-response :servlet-context))]
    (if (:websocket? request)
      (async/as-channel request :on-open (fn [ch] (async/send! ch data)))
      (response data))))

(defn with-charset [request]
  (let [[_ cs] (re-find #"charset=(.*)" (:query-string request))
        body "気になったら"]
    (charset {:headers {"BodyBytes" (pr-str (into [] (.getBytes body cs)))}
              :body body}
      cs)))

(defn chunked-stream [request]
  (update-in
    (async/as-channel request
      {:on-open
       (fn [stream]
         (dotimes [n 10]
           (async/send! stream (str n) {:close? (= n 9)})))})
    [:headers] assoc "ham" "biscuit"))

(defn non-chunked-stream [request]
  (async/as-channel request
    {:on-open
     (fn [stream]
       (async/send! stream (apply str (repeat 128 1)) {:close? true}))}))

(defn sse
  [request]
  (sse/as-channel request
    {:on-open (fn [ch]
                (doseq [x (range 5 0 -1)]
                  (sse/send! ch x))
                (sse/send! ch {:event "close", :data "bye!"}))}))

(defn ws-as-channel
  [request]
  (assoc
    (async/as-channel request
      {:on-message (fn [ch message]
                     (async/send! ch (.toUpperCase message)))
       :on-error (fn [ch err]
                   (println "Error on websocket")
                   (.printStackTrace err))})
    :headers {"ham" "biscuit"}
    :session (assoc (:session request) :ham :sandwich)))

(def client-defined-handler (atom (fn [_] (throw (Exception. "no handler given")))))

(def client-state (atom nil))

(defn set-client-handler [new-handler]
  (binding [*ns* (find-ns 'testing.app)]
    (reset! client-defined-handler (eval (read-string new-handler))))
  (response "OK"))

(defn use-client-handler [request]
  (@client-defined-handler request))

(defn get-client-state [_]
  (let [state (maybe-deref @client-state 30000 :failure!)]
    (when (nil? state) (println "CLIENT-STATE IS NIL!"))
    (-> state pr-str response)))

(def user-defined-servlet
  (let [events (atom nil)
        results (atom (promise))]
    (proxy [HttpServlet] []
      (service [servlet-request servlet-response]
        (let [ring-request (ring/ring-request-map servlet-request
                             [:servlet-request  servlet-request]
                             [:servlet-response servlet-response])
              ring-response (if (= "get-result" (:query-string ring-request))
                              (-> (maybe-deref @results 30000 :failure!) pr-str response)
                              (async/as-channel ring-request
                                :on-open (fn [stream]
                                           (dotimes [n 10]
                                             (async/send! stream (str n) {:close? (= n 9)})))))]
          (ring/write-response servlet-response ring-response)))
      (init [config]
        (proxy-super init config)
        (servlet/add-endpoint this config
          {:on-open    (fn [_]
                         (reset! events [:open]))
           :on-close   (fn [_ {c :code}]
                         (deliver @results (swap! events conj c)))
           :on-message (fn [_ m]
                         (swap! events conj m))})))))

(def wrapped-handler-servlet
  (let [events (atom nil)
        results (atom (promise))]
    (-> (fn [{:keys [websocket? query-string] :as req}]
          (if (= "get-result" query-string)
            (-> (maybe-deref @results 30000 :failure!) pr-str response)
            (async/as-channel req
              :on-open    (fn [ch]
                            (if websocket?
                              (reset! events [:open])
                              (dotimes [n 10]
                                (async/send! ch (str n) {:close? (= n 9)}))))
              :on-close   (fn [_ {c :code}]
                            (when websocket?
                              (deliver @results (swap! events conj c))))
              :on-message (fn [_ m]
                            (swap! events conj m)))))
      servlet/create-servlet)))

(defroutes routes
  (GET "/" [] counter)
  (GET "/session" {s :session} (encode s))
  (GET "/unsession" [] {:session nil})
  (GET "/charset" [] with-charset)
  (GET "/chunked-stream" [] chunked-stream)
  (GET "/non-chunked-stream" [] non-chunked-stream)
  (GET "/sse" [] sse))

(defroutes cdef-handler
  (ANY "/" [] use-client-handler)
  (GET "/set-handler" [new-handler] (set-client-handler new-handler))
  (GET "/state" [] get-client-state))

(defroutes nested-ws-routes
  (GET "/" [] dump)
  (GET "/foo" [] dump)
  (GET "/foo/bar" [] dump))

(defn run []
  (web/run (-> #'routes wrap-session))
  (web/run (-> #'cdef-handler wrap-params) :path "/cdef")
  (web/run (-> ws-as-channel wrap-session) :path "/ws")
  (web/run (-> dump wrap-session wrap-params) :path "/dump")
  (web/run user-defined-servlet :path "/user-defined-servlet")
  (web/run wrapped-handler-servlet :path "/wrapped-handler-servlet")
  (web/run nested-ws-routes :path "/nested-ws"))
