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

(def hostname (.getHostName (java.net.InetAddress/getLocalHost)))

(def save-folder
  "Folder to save incoming files"
  (File. (str (System/getProperty "user.dir") File/separatorChar  "public")))

(defn init
  "Init hook to create the save-folder path"
  []
  (when-not (.isDirectory save-folder)
    (.mkdirs save-folder)))

(defn create-dated-filename
  "Given a filename, returns the location inside 'save-folder' with date on the filename in order to view it later"
  [filename-str]
  (str
   save-folder
   File/separatorChar
   (ftime/unparse (ftime/formatters :basic-date-time)
                  (time/now))
   ;;optionally bind nanotime to avoid colisions
   #_(bit-and 0xFFFF (System/nanoTime)) 
   filename-str))

(defn buff-file-store
  "This function is used to handle submitted files by
   turning them to byte-arrays.
   Optionally can 'save-file' to specified 'save-folder'"
  [& {:keys [save-file]}]
  (fn [item]
    (let [stream (ByteArrayOutputStream.)]
      ;saves the stream
      (io/copy (:stream item) stream)

      (when save-file
        (let [filepath (File. (create-dated-filename (:filename item)))] (println "saving" (:stream item) "to" filepath)
             (.createNewFile filepath)
             (io/copy (.toByteArray stream) filepath)))

      ;;assoc the byte-array to the request
      (-> (select-keys item [:filename :content-type])
          (assoc :byte-array (.toByteArray stream))))))

(def index
  "The index page with a form to upload file and information about
   the server"
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
    "Files uploaded are " [:a {:href "public"} "available"] [:br]
    [:p
     "To post a file you can use" [:br]
     [:em "$ curl -X POST -F filename=@<filename> http://" hostname "/file"]]
    [:p
     "To delete a file you can use" [:br]
     [:em "$ curl -X DELETE http://" hostname "/file/<filename>"]]
    
    "Using store location" (.getAbsolutePath save-folder) [:br]
    ;; "Home folder is" (System/getProperty "user.home") [:br]

    ;; "user.home=" (System/getProperty "user.home") [:br]	
    ;; "user.dir=" (System/getProperty "user.dir") [:br]	
    ;; "java.io.tmpdir=" (System/getProperty "java.io.tmpdir") [:br]	
    ;; "java.home=" (System/getProperty "java.home") [:br]	
    ;; "catalina.home=" (System/getProperty "catalina.home") [:br]	
    ;; "catalina.base=" (System/getProperty "catalina.base") [:br]
    ]))	


(defn list-files
  "List files inside 'save-folder' in html format with last-modified date and erase button"
  []
  (html
   [:html
    [:body
     "Files uploaded were:" [:br]
     [:table
      [:thead
       [:tr [:td "Filename"] [:td "Last modified"] [:td "Erase?"]]]
      (for [i (sort-by #(.lastModified %) > (file-seq save-folder))]
        (when (.isFile i)
          [:tbody
           [:tr
            [:td [:a {:href (str "public/" (.getName i))}
                  (.getName i) [:br]]]
            [:td (->> (.lastModified i)
                      coertime/from-long
                      (ftime/unparse (ftime/formatters :date-time)))]
            [:td (form-to [:delete (str "file/" (.getName i))]
                          (submit-button "ERASE!"))[:a ]]]]))]]]))

(defn delete-file [filename]
  (let [f (File. (str save-folder File/separatorChar filename))
        name (.getName f)
        success (and (.isFile f)
                     (.delete f))]
    (html
     (if success
       (str name " deleted!")
       (str "error deleting " name)) [:br]
       [:a {:href "/public"} "Back"])))

(defroutes app-routes
  (GET "/" [] index)
  (POST "/file" req (do
                      #_(println req)
                      (ByteArrayInputStream. (-> req :params :filename :byte-array))))
  (context "/public" []
           (routes
            (GET "/index.htm" [] (list-files))
            (GET "/" [] (list-files))
            (route/files "/" {:root (.getAbsolutePath save-folder)})))
  (DELETE "/file/:filename" [filename] (delete-file filename))  
  (route/not-found "Not Found"))

(def app
  (-> (handler/api app-routes)
      (wrap-multipart-params {:store (buff-file-store :save-file true)})
      wrap-stacktrace-web))

(def server (atom nil))

(defn start-server []
  (when (not @server)
    (reset! server (run-jetty #'app {:port 8080 :join? false}))))
