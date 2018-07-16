(ns worktv.events
  (:require [re-frame.core :refer [reg-event-db reg-event-fx]]))

(def log js/console.log)

(reg-event-fx
 :init
 (fn [_ _]
   (log "init event...")
   (as-> effects {:db {}}
     (if (.startsWith (-> js/window .-location .-pathname) "/login")
       (assoc effects :dispatch [:get-user])
       effects))))

(reg-event-db
 :current-page
 (fn [db [_ page]]
   (assoc db :current-page page)))

(reg-event-fx
 :get-user
 (fn []))
