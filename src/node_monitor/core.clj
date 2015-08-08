(ns node-monitor.core
  (:gen-class)
  (:use
   org.httpkit.server
   ring.middleware.params
   [ring.middleware.json :only [wrap-json-body]]
   [ring.util.response :only [response]]
   )
  (:require
   [compojure.core :refer :all ]
   [compojure.route :as route]
   [compojure.handler :as handler]
   [ring.middleware.json :refer [wrap-json-response]]
   [clojure.data.json :as json]
   [clj-time.local :as l]
   )
)


(def default-port 8096)

(def config-file-name "node-monitor.cfg")

(defonce server (atom nil))

(def ^{:private true} check-node-status (atom true))

(def ^{:private true} node-info (atom {}))

(def config
  ^{:private true}
  (atom {}))


(defn init-config []
  (reset! config {
                  :interval 30000
                  :timeout 5000
                  :node-list '{}
                  }))

(defn save-config []
  (spit config-file-name @config))

(defn load-config []
  (reset! config (read-string (slurp config-file-name))))


(defn entry-name
  "Get the name of the entry or if undefined return the ip address"
  [entry]
  (or (:name entry) (:host entry)))


(defn- now
  "Get the current time in nano seconds"
  []
  (. System nanoTime))


(defn check-node-with-ping
  "Check if a node is available using .isReachable"
  [entry timeout]
  (let [addr (java.net.InetAddress/getByName (:host entry))
        start (now)
        result (.isReachable addr timeout)
        total (/ (double (- (now) start)) 1000000.0)
        ]

    (merge {
            :host (:host entry)
            :description (:description entry)
            :response_time total
            :active result
            }
           (when-let [name (:name entry)]
             {:name name}
             )
           (when (true? result)
             {:response_ts (l/format-local-time (l/local-now) :date-time) }
             )
           )))


(defn- check-node [entry]
  (future
    (swap! node-info update-in [(entry-name entry)]
           merge (check-node-with-ping entry (:timeout @config)))
    ))


(defn run-check-nodes []
  (future (while @check-node-status
            (do
              (doseq [entry (:node-list @config)]
                (check-node (second entry)))
              (Thread/sleep (:interval @config))
              ))))


(defn json-response
  ([^String data]
   (json-response 200 data)
   )

  ([^Integer code ^String data]
   { :status code
     :headers {"Content-Type" "application/json; charset=utf-8"
               "Access-Control-Allow-Origin" "*" }
     :body (str (json/write-str data))
     })
)


(defn- get-node-info [id]
  (if-let [resp (@node-info id)]
    (json-response resp)
    (json-response 404 (str "Node:" id " not found"))
))



(defn create-or-update-node-entry
  "Create or update an configuration entry"
  [entry]
  (when (map? entry)
    (let [host (:host entry)]
      (swap! config update-in [:node-list host] merge entry)
      (save-config)

      { :status 201
        :headers {"Content-Type" "application/json; charset=utf-8"
                  "Access-Control-Allow-Origin" "*" }
        :body ((:node-list @config) host)
        }
      )))


(defn remove-node-entry
  "Remove a configuration and the corresponding status entry"
  [host]
  (dosync
   (when-let [name (entry-name ((:node-list @config) host))]
     (swap! node-info dissoc name))
   (swap! config update-in [:node-list] dissoc host))
  (save-config)

  {
   :status 204
   :headers {"Content-Type" "application/json; charset=utf-8"
             "Access-Control-Allow-Origin" "*" }
   }
  )



(defroutes my-routes
  (GET "/nodes" [] (json-response @node-info))
  (GET "/nodes/" [] (json-response @node-info))

  (GET "/config" [] (json-response @config))
  (POST "/config" {body :body} (create-or-update-node-entry body))
  (DELETE "/config/:id" [id] (remove-node-entry id))

  (GET "/nodes/offline/" [] (json-response
                             (flatten (filter #(false? (:active (nth % 1))) @node-info))))

  (context "/nodes/:id" [id]
           (GET "/" [] (get-node-info id))
           )
  (route/not-found "Not Found 404")
)



(def handler
  (-> my-routes
      (wrap-json-body {:keywords? true})
      wrap-json-response
      ))


(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))


(defn start-server [port-no]
  (reset! server (run-server #'handler {:port port-no})))


(defn -main [& args]
  (let [port (Integer. (or (System/getenv "PORT") default-port))]
    (println "Using port:" port)

    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (fn []
                                 (println "Shutting down...")
                                 (reset! check-node-status false)
                                 (shutdown-agents)
                                 )))
    (load-config)
    (println @config)

    (println "Starting server")
    (start-server port)

    (println "Starting node checking")
    (run-check-nodes)
    ))
