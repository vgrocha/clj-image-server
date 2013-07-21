(defproject image-server "0.1.0-SNAPSHOT"
  :description "This is a small clojure server to store and retrieve images"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]
                 [hiccup "1.0.3"]
                 [ring "1.2.0"]]
  :plugins [[lein-ring "0.8.6"]]
  :ring {:handler image-server.handler/app
         :port 8080}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.5"]
                        #_[ring-serve "0.1.2"]]}})
