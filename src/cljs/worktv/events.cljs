(ns worktv.events
  (:require [re-frame.core :refer [reg-event-db reg-event-fx]]))

(def log js/console.log)

(reg-fx 
  :xhr
  (fn )
(reg-event-fx
 :init
 (fn [_ _]
   (log "init event...")
   (cond-> {:db {}}
     (cond (.startsWith (-> js/window .-location .-pathname) "/login")
       (assoc :dispatch [:get-user])))))

(reg-event-db
 :current-page
 (fn [db [_ page]]
   (assoc db :current-page page)))

(reg-event-fx
 :get-user
 (fn []))
