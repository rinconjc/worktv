(ns worktv.backend
  (:require [clojure.core.async :refer [>! chan]]
            [worktv.utils :as u]
            [ajax.core :refer [GET]]
            [clojure.string :as str]
            [cljsjs.firebase]
            [reagent.session :as session]
            [ajax.core :refer [POST]]
            [secretary.core :as secreatary]
            [reagent.session :as session]
            [cljs.core.match :refer-macros [match]]
            [secretary.core :as secreatary]
            [worktv.utils :refer [async-http]]
            [ajax.core :refer [PUT]]
            [worktv.utils :refer [async-http]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; (defonce f (js/firebase.initializeApp (clj->js {:apiKey "AIzaSyB-uyzpSf21QlMc9oAlXD82Dv6HuqHsb8U"
;;                                                 :authDomain "general-155419.firebaseapp.com"
;;                                                 :databaseURL "https://general-155419.firebaseio.com/"})))


(defn login-with-email [email]
  (go
    (match [(<! (async-http POST "/api/login" {:params {:email email}}))]
           [{:ok _}] (secreatary/dispatch! "/login-confirm")
           [{:error error}] (session/put! :error error))))

;; (defn get-user []
;;   (async-http GET "/api/user"))

;; (defn login [user password]
;;   (let [auth (.auth js/firebase)
;;         ch (chan)]
;;     (.onAuthStateChanged auth #(if % (go (>! ch [%]))))
;;     (-> auth (.signInWithEmailAndPassword user password)
;;         (.catch #(do
;;                    (js/console.log "error?" %)
;;                    (go (>! ch [nil, (.-message %)])))))
;;     ch))

(defn save-project [project]
  (async-http PUT (str "/api/projects" (:id project)) {:params project}))

(defn find-projects [name]
  (async-http GET "/api/projects" {:params {:name name}}))

;; (defn get-project [id]
;;   (async-http GET (str "/api/projects/" id)))

(defn publish-project [proj-id, path]
  (async-http POST (str "/api/publishings") {:params {:project-id proj-id :path path}}))

(defn extract-urls-from-google-results [html]
  (for [[_ url] (re-seq #"\"ou\":\"([^\"]\+)\"" html)] url))

(defn search-images [q]
  (if (or (nil? q) (some (partial str/starts-with? q) ["http://" "https://"]))
    (go q)
    (let [ch (chan)]
      (GET "/api/search" :format :json :response-format :json :keywords? true
           :params {:q q :type "image"}
           :handler #(go (>! ch %)))
      ch)))
