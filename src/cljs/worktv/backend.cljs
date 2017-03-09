(ns worktv.backend
  (:require [clojure.core.async :refer [>! chan]]
            [worktv.utils :as u])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce f (js/firebase.initializeApp (clj->js {:apiKey "AIzaSyB-uyzpSf21QlMc9oAlXD82Dv6HuqHsb8U"
                                                :authDomain "general-155419.firebaseapp.com"
                                                :databaseURL "https://general-155419.firebaseio.com/"})))

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
