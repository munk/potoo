(ns potoo.server
  (:require [com.stuartsierra.component :as component]
            [ring.adapter.jetty :refer [run-jetty]]
            [bidi.ring :refer [make-handler]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.util.response :refer [response]]
            [potoo.datomic :as db]
            [taoensso.timbre :as log]))

;; Helpers

(defn- fmt-potoos [data]
  (map (partial zipmap [:text :name :date]) data))

;; Handlers

(defn get-potoos [req]
  (let [data (db/find-potoos (:db-conn req))]
    (log/info "Getting all potoos from" (:remote-addr req))
    (response (fmt-potoos data))))

;; Routes

(def routes
  ["/api" {"/potoos" {:get {[""] get-potoos}}}])

;; Primary handler

(defn wrap-connection [handler conn]
  (fn [req] (handler (assoc req :db-conn conn))))

(defn potoo-handler [conn]
  (wrap-json-response (wrap-connection (make-handler routes) conn)))

;; WebServer

(defrecord WebServer [opts container datomic-connection]
  component/Lifecycle
  (start [component]
    (log/info "Starting web server with params:" opts)
    (let [conn (:db-conn datomic-connection)]
      (let [req-handler (potoo-handler conn)
            container (run-jetty req-handler opts)]
        (assoc component :container container))))
  (stop [component]
    (log/info "Stopping web server")
    (.stop container)
    component))

(defn new-server [opts]
  (WebServer. opts nil nil))