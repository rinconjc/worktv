(ns worktv.events
  (:require [ajax.core :refer [GET PUT]]
            [cljs.core.async :refer-macros [go]]
            [clojure.core.match :refer-macros [match]]
            [re-frame.core :refer [debug dispatch reg-event-db reg-event-fx reg-fx]]
            [worktv.utils :refer [async-http]]))

(def log js/console.log)

(reg-fx
 :xhr
 (fn [{:keys [req on-success on-error on-complete]}]
   (go
     (let [resp (<! (apply async-http req))]
       (match [resp]
              [{:ok result}] (when on-success
                               (dispatch (conj on-success result)))
              [{:error error}] (when on-error
                                 (dispatch (conj on-error error))))
       (when on-complete
         (dispatch (conj on-complete resp)))))))

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
 :assoc-in-db
 (fn [db [_ ks value]]
   (assoc-in db ks value)))

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
          :on-success [:assoc-in-db [:user]]}}))

(reg-event-db
 :open-project-search
 (fn [db _]
   (assoc db :project-search {:projects [] :status nil})))

(reg-event-db
 :close-project-search
 (fn [db _]
   (dissoc db :project-search)))

(reg-event-fx
 :find-projects
 (fn [{:keys[db]} [_ name]]
   {:db (assoc-in db [:project-search :status] :in-progress)
    :xhr {:req [GET "/api/projects" {:params {:name name}}]
          :on-success [:assoc-in-db [:project-search :projects]]
          :on-error [:assoc-in-db [:project-search :error ]]
          :on-complete [:assoc-in-db [:project-search :status] :done]}}))

(reg-event-fx
 :save-project
 (fn [_ [_ project]]
   {:xhr {:req [PUT (str "/api/projects" (:id project)) {:params project}]
          :on-success [:assoc-in-db [:alert] {:success "Project saved!"}]
          :on-error [:assoc-in-db [:alert] {:error "Failed to save"}]}}))

(reg-event-fx
 :load-project
 (fn [{:keys[db]} [_ project-id]]
   {:xhr {:req [GET (str "/api/projects/" project-id)]
          :on-success [:assoc-in-db [:current-project]]
          :on-error [:assoc-in-db [:alert :error]]}}))

(reg-event-fx
 :publish-project
 (fn[_ [_ project-id path]]
   {:xhr {:req [PUT (str "/projects/" path) {:params {:id project-id}}]
          :on-success [:assoc-in-db [:alert] {:success (str "Project Published to " path)}]
          :on-error [:assoc-in-db [:alert :error]]}}))
