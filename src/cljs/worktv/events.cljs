(ns worktv.events
  (:require [ajax.core :refer [GET POST PUT]]
            [cljs.core.async :refer-macros [go]]
            [clojure.core.match :refer-macros [match]]
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx]]
            [secretary.core :as secretary]
            [worktv.db :as db]
            [worktv.utils :refer [async-http]]))

(defn init-events
  "noop function to make a dummy require entry" [])

(def log js/console.log)

(defmulti load-content-data :content-type)
(defmethod load-content-data :default [_])
(defmethod load-content-data :custom [{:keys [url id template] :as pane}]
  (GET url :handler #(dispatch
                      [:assoc-in-db [:content-data id]
                       ((js/Handlebars.compile template) (clj->js (or % {})))])
       :response-format :json
       :error-handler #(js/console.log "failed fetching " url ";" %)))

(defmulti play-content :content-type)
(defmethod play-content :default [_])
(defmethod play-content :chart [pane])

(defmethod play-content :custom [{:keys [refresh] :as pane}]
  (let [timeout (js/setTimeout #(load-content-data pane) (* (Math/max 60 refresh) 1000))]
    (dispatch [:assoc-in-db [:timers] conj timeout])))

(defmethod play-content :slides [{:keys [id slides interval] :as pane}]
  (let [advance-fn (fn[]
                     (dispatch [:update-in [:current-project :layout id :active]
                                #(-> % inc (rem (count slides)))]))
        timeout (js/setTimeout advance-fn (* interval 1000))]
    (dispatch [:assoc-in-db [:timers] conj timeout])))

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
 :route
 (fn [route]
   (secretary/dispatch! route)))

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

(reg-fx
 :play
 (fn [layout]
   (doseq [[_ pane] layout :when (= :content-pane (:type pane))]
     (play-content pane))))

(reg-event-fx
 :init
 (fn [{:keys[db]} _]
   (cond-> {:db db}
     (not (.startsWith (-> js/window .-location .-pathname) "/login"))
     (assoc :dispatch [:get-user]))))

(reg-event-db
 :assoc-in-db
 (fn [db [_ ks value]]
   (assoc-in db ks value)))

(reg-event-db
 :updated-in-db
 (fn [db [_ ks f value]]
   (update-in db ks f value)))

(reg-event-fx :route (fn [_ [_ route]] {:route route}))

(reg-event-db
 :current-page
 (fn [db [_ page]]
   (assoc db :current-page page)))

(reg-event-fx
 :login-with-email
 (fn [_ [_ email]]
   {:xhr {:req [POST "/api/login" {:params {:email email}}]
          :on-success [:route "/login-confirm"]
          :on-error [:assoc-in-db [:alert :error]]}}))

(reg-event-fx
 :get-user
 (fn [_ _]
   {:xhr {:req [GET "/api/user"]
          :on-success [:assoc-in-db [:user]]}}))

(reg-event-db
 :hide-alert
 (fn [db _]
   (dissoc db :alert)))

(reg-event-db
 :open-project-search
 (fn [db _]
   (assoc db :project-search {:projects [] :status nil})))

(reg-event-db
 :project-search-select
 (fn [db [_ id]]
   (assoc-in db [:project-search :selected] id)))

(reg-event-fx
 :open-project
 (fn [{:keys[db]} [_ id]]
   (when-let [id  (or id (get-in db [:project-search :selected]))]
     {:dispatch-n [[:load-project id]
                   [:close-modal]]})))

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
 (fn [{:keys[db]} [_ info]]
   (let [proj (merge (:current-project db) info)]
     {:xhr {:req (if (:id proj)
                   [PUT (str "/api/projects/" (:id proj)) {:params proj}]
                   [POST "/api/projects" {:params proj}])
            :on-success [:project-saved]
            :on-error [:assoc-in-db [:alert] {:error "Failed to save"}]}
      :db (assoc db :current-project proj)})))

(reg-event-db
 :project-saved
 (fn [db [_ {:keys [id]}]]
   (cond-> (assoc db :alert {:success "Project saved!"})
     (some? id) (assoc-in [:current-project :id] id))))

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

(reg-event-fx
 :design
 (fn [{:keys [db]} [_ new?]]
   (cond-> {:route "/project" :db db}
     (or new? (nil? (:current-project db)))
     (update :db assoc :current-project db/blank-design))))

(reg-event-fx
 :update-pane
 (fn [{:keys [db]} [_ pane]]
   (let [path (or (:path pane) [(:id pane)])]
     {:db (update-in db [:current-project :layout] assoc-in path (dissoc pane :path))
      :dispatch [:close-modal]})))

(reg-event-db
 :edit-pane
 (fn [db [_ pane]]
   (assoc db :pane-dialog pane)))

(reg-event-db
 :modal
 (fn [db [_ data]]
   (assoc db :modal data)))

(reg-event-db
 :close-modal
 (fn[db _]
   (dissoc db :modal)))

(reg-event-db
 :select-pane
 (fn [db [_ id]]
   (if (= id (:selected-pane db))
     (dissoc db :selected-pane)
     (assoc db :selected-pane id))))

(defn selected-pane [db]
  (some->> (:selected-pane db)
           (get (get-in db [:current-project :layout]))))

(reg-event-db
 :split-pane
 (fn [db [_ orientation]]
   (if-let [pane (selected-pane db)]
     (let [pane1-id (-> pane :id (* 10) inc)
           pane2-id (inc pane1-id)]
       (-> db
           (update :current-project update :layout assoc
                   (:id pane) {:id (:id pane) :type :container-pane :orientation orientation
                               :pane1 pane1-id :pane2 pane2-id}
                   pane1-id (merge pane {:id pane1-id :type :content-pane})
                   pane2-id {:id pane2-id :type :content-pane})
           (dissoc :selected-pane)))
     (assoc db :alert {:error "Please select the pane to split first"
                       :fade-after 5}))))

(reg-event-db
 :delete-pane
 (fn [db _]
   (if-let [pane-id (:selected-pane db)]
     (let [parent-id (quot pane-id 10)
           sibling (-> db :current-project :layout
                       (get (if (odd? pane-id) (inc pane-id) (dec pane-id))))]
       (-> db (update-in [:current-project :layout]
                        #(-> % (assoc parent-id (assoc sibling :id parent-id))
                             (dissoc pane-id (inc pane-id) (dec pane-id))))
           (dissoc :selected-pane)))
     (assoc db :alert {:error "Please select the pane to split first" :fade-after 5}))))

(defn remove-nth [v n]
  (vec (concat (subvec v 0 n) (subvec v (inc n)))))

(reg-event-db
 :delete-slide
 (fn [db [_ pane index]]
   (update-in db [:current-project :layout (:id pane)]
              #(as-> (update pane :slides remove-nth index) pane
                 (if (>= index (count (:slides pane)))
                   (assoc pane :active (dec index)) pane)))))

(reg-event-db
 :slide-active
 (fn [db [_ pane index]]
   (-> db (assoc-in [:current-project :layout (:id pane) :active] index))))

(reg-event-fx
 :play-project
 (fn [{:keys [db]} [_]]
   {:play (-> db :current-project :layout)}))
