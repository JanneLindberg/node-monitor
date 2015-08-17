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

(def ^{:private true
       :doc "Keep track of lost nodes togheter with the time it was last available"}
  lost-nodes (atom {}))

(defn get-lost-nodes []
  @lost-nodes)

(def config
  ^{:private true}
  (atom {}))


(defn init-config []
  (reset! config {
                  :interval 30000
                  :timeout 5000
                  :nodes '{}
                  }))

(defn save-config []
  (spit config-file-name @config))

(defn load-config []
  (reset! config (read-string (slurp config-file-name))))


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
             {:update_time (l/format-local-time (l/local-now) :date-time) }
             )
           )))


(defn lost-node-entry
  "Build an entry to keep for a lost node"
  [entry]
  {
   :host (:host entry)
   :node_lost_time (l/format-local-time (l/local-now) :date-time)
   }
)


(defn node-status-changed [entry]
   (if (false? (:active entry))
     (swap! lost-nodes assoc-in [(:host entry)]  (lost-node-entry entry))
     (swap! lost-nodes dissoc (:host entry))
))


(defn- check-node
  [entry]
  (future
    (let [host (:host entry)
          updated (check-node-with-ping entry (:timeout @config))]

      (when (not= (:active (@node-info host )) (:active updated))
        (node-status-changed updated)
        )
      (swap! node-info update-in [(:host entry)] merge updated)
      )
    ))



(defn run-check-nodes
  "Run thru all the configurated nodes at the specified intervall"
  []
  (future (while @check-node-status
            (do
              (doseq [entry (:nodes @config)]
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
      (swap! config update-in [:nodes host] merge entry)
      (save-config)

      { :status 201
        :headers {"Content-Type" "application/json; charset=utf-8"
                  "Access-Control-Allow-Origin" "*" }
        :body ((:nodes @config) host)
        }
      )))


(defn remove-node-entry
  "Remove a configuration and the corresponding status entry"
  [host]
  (dosync
   (when-let [name (:host ((:nodes @config) host))]
     (swap! node-info dissoc name))
   (swap! config update-in [:nodes] dissoc host))
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

  (GET "/nodes/lost/" [] (json-response (get-lost-nodes)))

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
