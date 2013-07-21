(defproject image-server "0.1.0-SNAPSHOT"
  :description "This is a small clojure server to store and retrieve images"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]]
  :plugins [[lein-ring "0.8.5"]]
  :ring {:handler image-server.handler/app}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.5"]]}})
