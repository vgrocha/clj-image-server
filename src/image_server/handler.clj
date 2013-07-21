(ns image-server.handler
  (:use compojure.core)
  (:use hiccup.core
        hiccup.form
        [ring.adapter.jetty :only (run-jetty)]
        ring.middleware.stacktrace
        ring.middleware.multipart-params)
  (:require [clojure.java.io :as io])
  (:require [compojure.handler :as handler]
            [compojure.route :as route])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream InputStream File])
  #_(:use ring.util.serve))

(defn temp-file-store [item]
  (let [temp-file (File. "/tmp/tmpfile")]
    (io/copy (:stream item) temp-file)
    (-> (select-keys item [:filename :content-type])
        (assoc :tempfile temp-file
               :size (.length temp-file)))))

(defn buff-file-store [item]
  (println "Using buff-file-store")
  (let [out (ByteArrayOutputStream.)]
    (io/copy (:stream item) out)
    (io/copy (:stream item) (File. "resources/asd.jpg"))
    (-> (select-keys item [:filename :content-type])
        (assoc :byte-array (.toByteArray out)))))

(def form
  (html
   [:head
    [:title
     "File upload server"]]
   [:html
    (form-to {:enctype "multipart/form-data"} [:post "/upload-file"]
             "Chose file to process" [:br]
             (file-upload "filename")
             [:br]
             (submit-button "Send!"))]))


(defroutes app-routes
  (GET "/" [] form)
  (POST "/upload-file" req (do
                             #_(println req)
                             (ByteArrayInputStream. (-> req :params :filename :byte-array))))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> (handler/api app-routes)
      (wrap-multipart-params {:store buff-file-store})
      wrap-stacktrace-web))

(def server (atom nil))

(defn start-server []
  (when (not @server)
    (reset! server (run-jetty #'app {:port 8080 :join? false}))))
