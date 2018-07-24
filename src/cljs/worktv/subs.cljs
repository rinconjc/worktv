(ns worktv.subs

  (:require [re-frame.subs :refer [reg-sub]]
            [clojure.string :as str]))

(defn init-subs "noop function to make dummy require in core" [])

(reg-sub :current-page (fn [db _] (:current-page db)))

(reg-sub :modal (fn [db _] (:modal db)))

(reg-sub :user* (fn [db _] (:user db)))

(reg-sub
 :user
 :<- [:user*]
 (fn [user [_]]
   (some-> user (assoc :name (first (str/split (:email user) #"@"))))))

(reg-sub :project-search (fn [db _] (:project-search db)))

(reg-sub :alert* (fn [db _] (:alert db)))

(reg-sub
 :alert
 :<- [:alert*]
 (fn [{:keys [error success fade-after] :as alert} [_]]
   (js/console.log "alert was" (clj->js alert))
   (cond-> {:text (or error success) :fade-after (or fade-after 5)}
     (some? error) (assoc :type "danger")
     (map? error) (assoc :text (:error error)))))

(reg-sub :current-project (fn [db _] (:current-project db)))

(reg-sub :pane-dialog (fn [db _] (:pane-dialog db)))

(reg-sub :selected-pane-id (fn [db _] (:selected-pane db)))
