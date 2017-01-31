(ns worktv.layout
  (:require [ajax.core :refer [GET]]
            [cljs.core.async :refer [<! chan]]
            [cljs.reader :refer [read-string]]
            [commons-ui.core :as c]
            [reagent.core :as r :refer [atom track] :refer-macros [with-let]]
            [reagent.session :as session]
            [worktv.backend :as b]
            [worktv.splitter :refer [splitter]]
            [worktv.views :refer [modal modal-dialog save-form search-project-form]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn visit [x f] (f x) x)
;; (s/def ::pane-type )
;; (s/def ::pane (s/keys :req [::pane-type]))
;; layout = id->pane
;; pane = content-pane | container-pane
;; content-pane = content-type src attrs
;; container-pane = {:pane1 p1 :pane2 p2}
;; 1-> 11 12 -> 111 112, 121 122
(def content-types [{:type :image :label "Image"}
                    {:type :video :label "Video"}
                    {:type :custom :label "Custom"}
                    {:type :chart :label "Chart"}
                    {:type :slide :label "Slide"}])

(def current-design
  (let [model (session/cursor [:current-design])]
    (when-not @model
      (session/put! :current-design {:layout {1 {:id 1 :type :content-pane}} :screen "1280x720"}))
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

(defmulti content-editor :content-type)

(defmethod content-editor :image [pane]
  [:div.form
   [:div.form-group
    [:label {:for "title"} "Title"]
    [:input.form-control
     {:defaultValue (:title pane) :id "title" :placeholder "Optional Title"
      :on-change #(update-pane (assoc pane :title (-> % .-target .-value)))}]]
   [:div.form-group
    [:label {:for "url"} "URL"]
    [:input.form-control
     {:defaultValue (:url pane) :id "url" :placeholder "Image URL"
      :on-change #(update-pane (assoc pane :url (-> % .-target .-value)))}]]
   [:div.form-group
    [:label {:for "display"} "Display"]
    [:select.form-control
     {:id "display"
      :defaultValue (:display pane)
      :on-change #(update-pane (assoc pane :display (-> % .-target .-value)))}
     [:option {:value "fit-full"} "Fill"]
     [:option {:value "clipped"} "Clip"]]]])

(defmethod content-editor :custom [pane]
  [:div.form
   [:div.form-group
    [:label {:for "title"} "Title"]
    [:input.form-control
     {:defaultValue (:title pane) :id "title" :placeholder "Optional Title"
      :on-change #(update-pane (assoc pane :title (-> % .-target .-value)))}]]
   [:div.form-group
    [:label {:for "url"} "Data URL"]
    [:input.form-control
     {:defaultValue (:url pane) :id "url" :placeholder "URL of data"
      :on-change #(update-pane (assoc pane :url (-> % .-target .-value)))}]]
   [:div.form-group
    [:label {:for "template"} "HTML Template"]
    [:textarea.form-control
     {:defaultValue (:template pane) :id "template" :placeholder "mustache template"
      :rows 10
      :on-change #(update-pane (assoc pane :template (-> % .-target .-value)))}]]])

(defn editor-dialog [pane-id]
  (let [model (track pane-by-id pane-id)]
    [:div.modal {:style {:display "block"} :tabIndex -1}
     [:div.modal-dialog
      [:div.modal-content
       [:div.modal-header [:h4 "Pane Content"]]
       [:div.modal-body
        (content-editor @model)]
       [:div.modal-footer
        [:button.btn {:on-click #(reset! modal nil)} "Close"]]]]]))

(defn show-editor [model]
  (reset! modal [editor-dialog (:id model)]))

(defmulti content-view :content-type)

(defmulti pane-view :type)

(defmethod pane-view :content-pane [pane]
  [:div.fill.full
   {:on-click #(do
                 (reset! selected-pane-id (if (is-selected pane) nil (:id pane)))
                 false)
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
  [:video {:src url :class (if fill? "fill" "")}])

(defn data-view [url template]
  (let [data (atom nil)
        last-url (atom url)]
    (fn [url template]
      (when-not (and @data (= @last-url url))
        (GET url :handler #(reset! data %)
             :error-handler #(js/console.log "failed fetching " url ";" %))
        (reset! last-url url))
      [:div.fit {:style {:overflow "hidden"} :dangerouslySetInnerHTML
                 {:__html (js/Mustache.render template (clj->js @data))}}])))

(defmethod content-view :custom [{:keys [url template title]}]
  (if (and url template)
    [data-view url template]
    [:div.fit "Missing url and/or template"]))

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
    (reset! alert [c/alert {:type "danger"}
                   "Please select the pane to split first"])))

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
            (reset! alert [c/alert {:type "success" :fade-after 5} "Project saved"]))
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
       [:div.col-md-2.fill
        [:h2 "Dashboard Designer"]
        [:div.panel.panel-default
         [:div.panel-heading "Widgets"]
         [:div#content-nav.list-group
          (doall
           (for [{:keys [type label]} content-types]
             ^{:key type}
             [:button.list-group-item
              {:draggable true :on-drag-start (drag-start (name type))} label]))]]

        (if-let [pane (selected-pane)]
          [:div.panel.panel-default
           [:div.panel-heading "Selected"]
           [:div.panel-body
            [:select.form-control
             {:value (-> pane :content-type (or ""))
              :on-change #(update-pane (assoc pane :content-type (-> % .-target .-value keyword)))}
             [:option ""]
             (doall
              (for [{:keys [type label]} content-types]
                ^{:key type}[:option {:value type} label])) ]
            [:button.btn {:on-click #(show-editor pane)}
             "..."]]])]
       [:div.col-md-10.fill
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
            [:button.btn.btn-default {:title "New Project"}
             [:i.glyphicon.glyphicon-file] "New"]
            [:button.btn.btn-default {:title "Save Project" :on-click handle-save-project}
             [:i.glyphicon.glyphicon-save] "Save"]
            [:button.btn.btn-default {:title "Preview Project"}
             [:i.glyphicon-glyphicon-facetime-video] "Preview"]]]]]
        [:div.row.fill {:style {:padding-bottom "60px"}}
         [:div.col-md-12.fill.full {:style {:background-color "#f5f5f5"}}
          [:div#layoutBox.fill
           [layout-editor]]]]]])))
