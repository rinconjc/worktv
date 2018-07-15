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
            [secretary.core :as secreatary])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce f (js/firebase.initializeApp (clj->js {:apiKey "AIzaSyB-uyzpSf21QlMc9oAlXD82Dv6HuqHsb8U"
                                                :authDomain "general-155419.firebaseapp.com"
                                                :databaseURL "https://general-155419.firebaseio.com/"})))

;; ============= promise to channel ============
(defn to-chan [p]
  (let [ch (chan)]
    (-> p
        (.then #(go (>! ch {:ok %})))
        (.catch #(go (>! ch {:error %}))))
    ch))

(defn map-chan [ch f]
  (go (let [{:keys [success] :as r} (<! ch)]
        (if success {:ok (f success)} r))))

(defn flat-map-chan [ch f]
  (go (let [{:keys [success] :as r} (<! ch)]
        (if success (<! (f success)) r))))

;; ========================

(defn async-http [method uri opts]
  (let [ch (chan)]
    (method uri
            (assoc opts
                   :handler #(go
                               (js/console.log "success " uri)
                               (>! ch {:ok %}))
                   :format :json
                   :response-format :json
                   :error-handler #(go
                                     (js/console.log "error" uri %)
                                     (>! ch {:error (:response %)}))
                   :keywords? true))
    ch))

(defn login-with-email [email]
  (go
    (match [(<! (async-http POST "/api/login" {:params {:email email}}))]
           [{:ok _}] (secreatary/dispatch! "/login-confirm")
           [{:error error}] (session/put! :error error))))

(defn login [user password]
  (let [auth (.auth js/firebase)
        ch (chan)]
    (.onAuthStateChanged auth #(if % (go (>! ch [%]))))
    (-> auth (.signInWithEmailAndPassword user password)
        (.catch #(do
                   (js/console.log "error?" %)
                   (go (>! ch [nil, (.-message %)])))))
    ch))

(defn save-project [project]
  (let [ch (chan)
        db (.database js/firebase)
        dir (str "projects/" (:folder project))
        id (or (:id project) (-> db (.ref dir) .push .-key))]
    (-> db (.ref (str dir "/" id)) (.set (-> project (dissoc :id) clj->js))
        (.then #(go (>! ch id)))
        (.catch #(go (>! ch {:error %}))))
    ch))

(defn find-projects [user-id]
  (let [db (.database js/firebase)
        ch (chan)]
    (-> db (.ref (str "projects/public"))
        (.once "value" (fn [snapshot]
                         (go
                           (let [ps (transient {})]
                             (.forEach snapshot #(and (assoc! ps (.-key %) (.val %)) nil))
                             (>! ch (persistent! ps)))))))
    ch))

(defn get-project [path]
  (let [db (.database js/firebase)
        ch (chan)]
    (-> db (.ref (str "projects/" path))
        (.once "value") (.then #(go (>! ch [(-> % .val)]))))
    ch))

(defn publish-project [user-key {:keys [id name folder]}]
  (let [db (.database js/firebase)
        path (str "published/" (if (= "public" folder) "public" user-key) "/" id)
        ch (chan)]
    (-> db (.ref path)
        (.set #js {:name name :date (js/Date.)})
        (.then #(go (>! ch [path])))
        (.catch #(go (>! ch [nil %]))))
    ch))

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
