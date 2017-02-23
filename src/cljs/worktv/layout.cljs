(ns worktv.layout
  (:require [ajax.core :refer [GET]]
            [cljs.core.async :refer [<! chan]]
            [cljs.reader :refer [read-string]]
            [commons-ui.core :as c]
            [reagent.core :as r :refer [atom] :refer-macros [with-let]]
            [reagent.session :as session]
            [worktv.backend :as b]
            [worktv.splitter :refer [splitter]]
            [worktv.utils :as u]
            [worktv.views
             :as
             v
             :refer
             [chart-form modal modal-dialog save-form search-project-form]]
            [cljsjs.mustache])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def content-types [{:type :image :label "Image"}
                    {:type :video :label "Video"}
                    {:type :custom :label "Custom"}
                    {:type :chart :label "Chart"}
                    {:type :slide :label "Slide"}])

(def blank-design {:layout {1 {:id 1 :type :content-pane}} :screen "1280x720"})

(def current-design
  (let [model (session/cursor [:current-design])]
    (when-not @model
      (session/put! :current-design blank-design))
    model))

(def selected-pane-id (atom nil))
(def alert (atom nil))

(defn data-from [url refresh-rate]
  (let [data (atom nil)]
    (GET url :handler #(reset! data %) :error-handler #(js/console.log "failed retrieving " url (clj->js %)))
    data))

(defn pane-by-id [id]
  (-> @current-design :layout (get id)))

(defn selected-pane []
  (if @selected-pane-id (-> @current-design :layout (get @selected-pane-id))))

(defn is-selected [pane]
  (= (:id pane) @selected-pane-id))

(defn update-pane [pane]
  (swap! current-design update :layout assoc (:id pane) pane)
  pane)

(defmulti content-editor (comp :content-type deref))

(defmethod content-editor :image [pane]
  [v/image-form pane])

(defmethod content-editor :custom [pane]
  [v/custom-form pane])

(defmethod content-editor :video [pane]
  [:form.form
   [c/input {:type "text" :label "Title" :model [pane :title]}]
   [c/input {:type "text" :label "Video URL" :model [pane :url]}]])

(defmethod content-editor :chart [pane]
  [chart-form pane])

(defn editor-dialog [pane-id]
  (with-let [model (atom (pane-by-id pane-id))]
    [modal-dialog {:title (str "Edit " (-> @model :content-type name) " details")
                   :ok-fn #(go (update-pane @model))}
     (content-editor model)]))

(defn show-editor [model]
  (reset! modal [editor-dialog (:id model)]))

(defmulti content-view :content-type)

(defmulti pane-view :type)

(defmethod pane-view :content-pane [pane]
  [:div.fill.full
   {:on-click #(do
                 (reset! selected-pane-id (if (is-selected pane) nil (:id pane)))
                 false)
    :on-double-click #(do (.preventDefault %) (show-editor pane))
    :on-drag-over #(.preventDefault %)
    :on-drag-enter #(-> % .-target .-classList (.add "drag-over") (and false))
    :on-drag-leave #(-> % .-target .-classList (.remove "drag-over") (and false))
    :on-drag-end #(-> % .-target .-classList (.remove "drag-over") (and false))
    :on-drop #(do
                (js/console.log "type:" (-> % .-dataTransfer (.getData "text/plain")))
                (show-editor
                 (update-pane (assoc pane :content-type (-> % .-dataTransfer (.getData "text/plain") keyword)))))
    :class (if (is-selected pane)  "selected-pane")}
   (content-view pane)])

(defmethod pane-view :container-pane [{:keys [pane1 pane2] :as opts}]
  [splitter opts
   (pane-view (pane-by-id pane1))
   (pane-view (pane-by-id pane2)) update-pane])

(defmethod content-view :image [{:keys [url display title]}]
  [:div.full.fill
   (if title [:h4.top-relative title])
   [:img.fit {:src url :class display}]])

(defmethod content-view :video [{:keys [url fill? title]}]
  (if (and url (re-find #"youtube.com" url))
    [:div.fill.full {:style {:padding "40px"}}
     [:iframe {:frame-border 0 :src url :style {:margin "5px" :width "100%" :height "100%"}} ]]
    [:video {:src url :class (if fill? "fill" "")}]))

(defn data-view [url template refresh]
  (with-let [data (atom nil)
             last-url (atom url)
             last-refresh (atom nil)
             load (fn []
                    (GET url :handler #(reset! data %) :response-format :json
                         :error-handler #(js/console.log "failed fetching " url ";" %))
                    (reset! last-url url))
             interval-id (atom nil)]
    (when (not= refresh @last-refresh)
      (if @interval-id (.clearInterval js/window @interval-id))
      (if (and refresh (> refresh 0))
        (reset! interval-id (js/setInterval load (* refresh 1000))))
      (reset! last-refresh refresh))
    (if-not (and @data (= @last-url url)) (load))
    [:div.fit {:style {:overflow "hidden"} :dangerouslySetInnerHTML
               {:__html (js/Mustache.render template (clj->js (or @data "no data")))}}]))

(defmethod content-view :custom [{:keys [url template title refresh-interval]}]
  (if (and url template)
    [data-view url template refresh-interval]
    [:div.fit "Missing url and/or template"]))

(defmethod content-view :chart [pane]
  [v/chart-view pane])

(defmethod content-view :default [pane]
  [:div.fill "blank content"])

(defn layout-editor []
  [:div.fill.full
   @alert
   @modal
   (pane-view (pane-by-id 1))])

(defn split-pane [orientation]
  (if-let [pane (selected-pane)]
    (let [pane1-id (-> pane :id (* 10) inc)
          pane2-id (inc pane1-id)]
      (swap! current-design update :layout assoc
             (:id pane) {:id (:id pane) :type :container-pane :orientation orientation
                         :pane1 pane1-id :pane2 pane2-id}
             pane1-id {:id pane1-id :type :content-pane}
             pane2-id {:id pane2-id :type :content-pane})
      (reset! selected-pane-id nil))
    (reset! alert (c/alert {:type "danger"}
                           "Please select the pane to split first"))))

(defn delete-pane []
  (if-let [pane-id @selected-pane-id]
    (let [parent-id (quot pane-id 10)
          sibling (-> @current-design :layout
                      (get (if (odd? pane-id) (inc pane-id) (dec pane-id))))]
      (update-pane (assoc sibling :id parent-id))
      (swap! current-design update :layout dissoc pane-id (inc pane-id) (dec pane-id))
      (reset! selected-pane-id nil))
    (reset! alert ["danger" "Please select the pane to split first"])))

(defn save-project [form]
  (let [ch (chan)]
    (go (let [result (<! (b/save-project
                          (-> @current-design (update :layout pr-str)
                              (merge form))))]
          (when-not (:error result)
            (swap! current-design assoc :id result)
            (reset! alert (c/alert {:type "success" :fade-after 6} "Project saved")))
          (>! ch result)))
    ch))

(defn handle-save-project []
  (if (:id @current-design) (save-project nil)
      (reset! modal (with-let [form (atom {})]
                      [modal-dialog {:title "Save Design..." :ok-fn #(save-project @form)}
                       [save-form form]]))))

(defn open-project [[key selected]]
  (let [ch (chan)]
    (go
      (if-not selected (>! ch {:error "Select a project!"})
              (do
                (reset! current-design
                        (-> selected
                            (js->clj :keywordize-keys true)
                            (update :layout read-string)
                            (assoc :id key)))
                (>! ch :ok))))
    ch))

(defn handle-open-project []
  (go
    (let [projs (<! (b/find-projects (.-uid (session/get :user))))
          selection (atom nil)]
      (reset! modal [modal-dialog {:title "Open a design..."
                                   :ok-fn #(open-project @selection)}
                     [search-project-form projs selection]]))))

(defn design-page []
  (let [drag-start (fn [type] #(-> % .-dataTransfer (.setData "text/plain" type)))]
    (fn []
      [:div.row-fluid.fill
       [:div.col-md-1.fill
        [:h2 "Dash..."]
        [:div.panel.panel-default
         [:div.panel-heading "Widgets"]
         [:div#content-nav.list-group
          (doall
           (for [{:keys [type label]} content-types]
             ^{:key type}
             [:button.list-group-item
              {:draggable true :on-drag-start (drag-start (name type))} [:i.fa {:class label}] label]))]]]
       [:div.col-md-11.fill
        [:div.row
         [:div.col-md12.fill {:style {:padding "10px"}}
          [:div.form-inline
           [:label.form-label "Screen size"]
           [:input.form-control {:value (:screen @current-design)
                                 :on-change #(swap! current-design assoc :screen (-> % .-target .-value))}]
           [:div.btn-group

            [:button.btn.btn-default
             {:on-click #(split-pane :vertical) :title "Split pane vertically"}
             [:i.fa.fa-columns.fa-fw.fa-rotate-270] "Vertical"]

            [:button.btn.btn-default
             {:on-click #(split-pane :horizontal) :title "Split pane horizontally"}
             [:i.fa.fa-columns.fa-fw] "Horizontal"]

            [:button.btn.btn-default
             {:on-click #(delete-pane) :title "Delete selected pane"}
             [:i.fa.fa-trash.fa-fw] "Delete"]]
           [:div.btn-group
            [:button.btn.btn-default {:title "Open Project" :on-click handle-open-project}
             [:i.glyphicon.glyphicon-open] "Open"]
            [:button.btn.btn-default {:title "New Project"
                                      :on-click #(reset! current-design blank-design)}
             [:i.glyphicon.glyphicon-file] "New"]
            [:button.btn.btn-default {:title "Save Project" :on-click handle-save-project}
             [:i.glyphicon.glyphicon-save] "Save"]
            [:button.btn.btn-default {:title "Preview Project"}
             [:i.glyphicon-glyphicon-facetime-video] "Preview"]]]]]
        [:div.row.fill {:style {:padding-bottom "60px"}}
         [:div.col-md-12.fill.full {:style {:background-color "#f5f5f5"}}
          [:div#layoutBox.fill
           [layout-editor]]]]]])))
