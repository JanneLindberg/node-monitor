(defproject node_monitor "0.1.0-SNAPSHOT"
  :description "Simple monitor to check if nodes is available"
  :url "https://github.com/JanneLindberg/node-monitor.git"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [http-kit "2.1.16"]
                 [ring "1.3.1"]
                 [ring/ring-jetty-adapter "1.3.1"]
                 [compojure "1.3.4"]
                 [org.clojure/data.json "0.2.5"]
                 [ring/ring-json "0.2.0"]
                 ]
  :profiles {:uberjar {:main node_monitor.core, :aot :all}}

)
