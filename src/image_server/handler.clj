(ns image-server.handler
  (:use compojure.core)
  (:use hiccup.core
        hiccup.form
        [ring.adapter.jetty :only (run-jetty)]
        ring.middleware.stacktrace
        ring.middleware.multipart-params)
  (:require [clojure.java.io :as io])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [clj-time
             [core :as time]
             [format :as ftime]])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream File]
           (org.eclipse.jetty.server Request)))

(def save-folder
  "Folder to save incoming files"
  (File. (or (System/getenv "user.home") "public")))

(defn init
  "Init hook to create the destination folder"
  []
  (when-not (.isDirectory save-folder)
    (.mkdirs save-folder)))

;;This was from a test from ring functions
#_(defn temp-file-store [item]
  (let [temp-file (File. "/tmp/tmpfile")]
    (io/copy (:stream item) temp-file)
    (-> (select-keys item [:filename :content-type])
        (assoc :tempfile temp-file
               :size (.length temp-file)))))



(defn get-dated-filename
  "Given a filename, returns the location inside 'save-folder' with date on the filename in order to view it later"
  [filename-str]
  (str
   save-folder
   File/separatorChar
   (ftime/unparse (ftime/formatters :basic-date-time)
                  (time/now))
   #_(bit-and 0xFFFF (System/nanoTime))
   filename-str))

(defn buff-file-store
  "Save file and pass it along the request as byte array"
  [& {:keys [save-file?]}]
  (fn [item]
    (let [stream (ByteArrayOutputStream.)
          filepath (File. (get-dated-filename (:filename item)))]
      (io/copy (:stream item) stream)

      (println "saving" (:stream item) "to" filepath)
      (.createNewFile filepath)
      (io/copy (.toByteArray stream) filepath)

      (-> (select-keys item [:filename :content-type])
          (assoc :byte-array (.toByteArray stream))))))

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
             (submit-button "Send!"))
    "Files uploaded are" [:a {:href "/public"} "available"]]))


(def root (str (System/getProperty "user.dir") "/public"))

(defn list-files
  "List files inside 'save-folder' in html format"
  []
  (html
   [:html
    "Files uploaded were:" [:br]
    (for [i (file-seq save-folder)]
      (when (.isFile i)
        [:a {:href (str "/public/" (.getName i))}
         (.getName i) [:br]]))]))

(defroutes app-routes
  (GET "/" [] form)
  (POST "/upload-file" req (do
                             #_(println req)
                             (ByteArrayInputStream. (-> req :params :filename :byte-array))))
  (route/resources "/asd")
  (context "/public" []
           (routes
            (GET "/index.htm" [] (list-files))
            (GET "/" [] (list-files))
            (route/files "/")))
  (route/not-found "Not Found"))

(def app
  (-> (handler/api app-routes)
      (wrap-multipart-params {:store (buff-file-store)})
      wrap-stacktrace-web))

(def server (atom nil))

(defn start-server []
  (when (not @server)
    (reset! server (run-jetty #'app {:port 8080 :join? false})))
  )
