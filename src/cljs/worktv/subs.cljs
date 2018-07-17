(ns worktv.subs

  (:require [re-frame.subs :refer [reg-sub]]
            [clojure.string :as str]))

(reg-sub :current-page (fn [db _] (:current-page db)))

(reg-sub :_user (fn [db _] (:user db)))

(reg-sub
 :user
 :<- [:_user]
 (fn [[user] [_]]
   (js/console.log "user:" user)
   (some-> user (assoc :name (first (str/split (:email user) #"@"))))))
