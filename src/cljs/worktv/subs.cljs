(ns worktv.subs

  (:require [re-frame.subs :refer [reg-sub]]
            [clojure.string :as str]))

(reg-sub :current-page (fn [db _] (:current-page db)))

(reg-sub :user* (fn [db _] (:user db)))

(reg-sub
 :user
 :<- [:user*]
 (fn [user [_]]
   (some-> user (assoc :name (first (str/split (:email user) #"@"))))))

(reg-sub :project-search (fn [db _] (:project-search db)))
