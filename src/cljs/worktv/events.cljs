(ns worktv.events
  (:require [ajax.core :refer [GET]]
            [cljs.core.async :refer-macros [go]]
            [clojure.core.match :refer-macros [match]]
            [re-frame.core :refer [debug dispatch reg-event-db reg-event-fx reg-fx]]
            [worktv.utils :refer [async-http]]))

(def log js/console.log)

(reg-fx
 :xhr
 (fn [{:keys [req dispatch-ok dispatch-error]}]
   (go
     (match [(<! (apply async-http req))]
            [{:ok resp}] (dispatch (conj dispatch-ok resp))
            [{:error error}] (when dispatch-error
                               (dispatch (conj dispatch-error error)))))))

(reg-fx
 :dispatch-chan
 (fn [[event ch & more]]
   (go
     (let [value (<! ch)]
       (js/console.log "ch msg received" value)
       (if (map? event)
         (match [value]
                [{:ok x}]
                (dispatch (into [(:ok event) x] more))
                [{:error error}]
                (dispatch (into [(:error event) error] more)))
         (dispatch (into [event value] more)))))))

(reg-event-fx
 :init
 [debug]
 (fn [{:keys[db]} _]
   (cond-> {:db db}
     (not (.startsWith (-> js/window .-location .-pathname) "/login"))
     (assoc :dispatch [:get-user]))))

(reg-event-db
 :current-page
 [debug]
 (fn [db [_ page]]
   (assoc db :current-page page)))

(reg-event-fx
 :get-user
 [debug]
 (fn [_ _]
   {:xhr {:req [GET "/api/user"]
          :dispatch-ok [:user-found]}}))

(reg-event-db
 :user-found
 [debug]
 (fn [db [_ user]]
   (assoc db :user user)))
