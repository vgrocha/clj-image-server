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
             [format :as ftime]
             [coerce :as coertime]])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream File]
           (org.eclipse.jetty.server Request)))

(def save-folder
  "Folder to save incoming files"
  (File. (str (System/getProperty "user.dir") File/separatorChar  "public")))

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
    (form-to {:enctype "multipart/form-data"} [:post "file"]
             "Chose file to process" [:br]
             (file-upload "filename")
             [:br]
             (submit-button "Send!"))
    "Files uploaded are" [:a {:href "public"} "available"] [:br]
    "Using store location" (.getAbsolutePath save-folder) [:br]
    "Home folder is" (System/getProperty "user.home") [:br]

    "user.home=" (System/getProperty "user.home") [:br]	
    "user.dir=" (System/getProperty "user.dir") [:br]	
    "java.io.tmpdir=" (System/getProperty "java.io.tmpdir") [:br]	
    "java.home=" (System/getProperty "java.home") [:br]	
    "catalina.home=" (System/getProperty "catalina.home") [:br]	
    "catalina.base=" (System/getProperty "catalina.base") [:br]]))	


(def root (str (System/getProperty "user.dir") "/public"))

(defn list-files
  "List files inside 'save-folder' in html format"
  []
  (html
   [:html
    [:body
     "Files uploaded were:" [:br]
     [:table
      [:thead
       [:tr [:td "Filename"] [:td "Last modified"]]]
      (for [i (sort-by #(.lastModified %) > (file-seq save-folder))]
        (when (.isFile i)

          [:tbody
           [:tr
            [:td [:a {:href (str "public/" (.getName i))}
                  (.getName i) [:br]]]
            [:td (->> (.lastModified i)
                      coertime/from-long
                      (ftime/unparse (ftime/formatters :date-time)))]]]))]]]))

(defroutes app-routes
  (GET "/" [] form)
  (POST "/file" req (do
                      #_(println req)
                      (ByteArrayInputStream. (-> req :params :filename :byte-array))))
  (context "/public" []
           (routes
            (GET "/index.htm" [] (list-files))
            (GET "/" [] (list-files))
            (route/files "/" {:root (.getAbsolutePath save-folder)})))
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
