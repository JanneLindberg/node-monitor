(ns node-monitor.core
  (:gen-class)
  (:use
   org.httpkit.server
   ring.middleware.json
   ring.middleware.params
   )
  (:require
   [compojure.core :refer :all ]
   [compojure.route :as route]
   [compojure.handler :as handler]
   [clojure.data.json :as json]
   )
)


(def default-port 8096)
(def config-file-name "node-monitor.cfg")

(defonce server (atom nil))

(def ^{:private true} check-node-status (atom true))

(def ^{:private true} node-info (atom {}))

(def config
  ^{:private true}
  (atom {
         :interval 30000
         :timeout 5000
         :node-list '[]
   }))


(defn- now
  "Get the current time in nano seconds"
  []
  (. System nanoTime))


(defn check-node-with-ping
  "Check if a node is available using .isReachable"
  [entry timeout]
  (let [addr (java.net.InetAddress/getByName (:ip entry))
        start (now)
        result (.isReachable addr timeout)
        total (/ (double (- (now) start)) 1000000.0)
        ]

    (merge {
            :host (:ip entry)
            :description (:description entry)
            :reponse-time total
            :active result
            }
           (when (true? result)
             {:response-ts (System/currentTimeMillis)}
             )
           )))


(defn- check-node [entry]
  (future
    (swap! node-info update-in [(:ip entry)]
           merge (check-node-with-ping entry (:timeout @config)))
    ))


(defn run-check-nodes []
  (future (while @check-node-status
            (do
              (doseq [entry (:node-list @config)]
                (check-node entry))
              (Thread/sleep (:interval @config))
              ))))

(defn- json-response
  [data]
  { :status 200
    :headers {"Content-Type" "application/json"}
    :body (str (json/write-str data))
   })


(defn- json-response-four-o-four
  "Wrap an data object in a"
  [data]
  { :status 404
    :headers {"Content-Type" "application/json"}
    :body (str (json/write-str data))
   })


(defn- get-node-info [id]
  (if-let [resp (@node-info id)]
    (json-response resp)
    (json-response-four-o-four (str "Node:" id " not found"))
))


(defroutes my-routes
  (GET "/nodes" [] (json-response @node-info))
  (GET "/nodes/" [] (json-response @node-info))

  (GET "/nodes/offline/" [] (json-response (filter #(false? (:active (nth % 1))) @node-info)))

  (context "/nodes/:id" [id]
           (GET "/" [] (get-node-info id))
           )
  (route/not-found "Not Found 404")
)


(def handler
  (-> my-routes
      wrap-params
      wrap-json-response
      wrap-json-body
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

    (reset! config (read-string (slurp config-file-name)))
    (println @config)

    (println "Starting server")
    (start-server port)

    (println "Staring node checking")
    (run-check-nodes)
    ))
