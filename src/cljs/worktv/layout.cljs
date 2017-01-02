(ns worktv.layout
  (:require [reagent.core :refer [atom track]]
            [reagent.session :as session]
            [worktv.splitter :refer [splitter]]))

;; (s/def ::pane-type )
;; (s/def ::pane (s/keys :req [::pane-type]))
;; layout = id->pane
;; pane = content-pane | container-pane
;; content-pane = content-type src attrs
;; container-pane = {:pane1 p1 :pane2 p2}
;; 1-> 11 12 -> 111 112, 121 122
(def content-types [{:type :image :label "Image"}
                    {:type :video :label "Video"}
                    {:type :badges :label "Badges"}
                    {:type :table :label "Table"}
                    {:type :chart :label "Chart"}
                    {:type :slide :label "Slide"}])

(def current-design
  (let [model (session/cursor [:current-design])]
    (when-not @model
      (session/put! :current-design {:layout {1 {:id 1 :type :content-pane}} :screen "1280x720"}))
    model))

(defn pane-by-id [id]
  (-> @current-design :layout (get id)))

(def selected-pane-id (atom nil))
(def alert (atom nil))
(def modal (atom nil))

(defn selected-pane []
  (if @selected-pane-id (-> @current-design :layout (get @selected-pane-id))))

(defn is-selected [pane]
  (= (:id pane) @selected-pane-id))

(defn update-pane [pane]
  (swap! current-design update :layout assoc (:id pane) pane))

(defmulti content-editor :content-type)

(defmethod content-editor :image [pane]
  [:div.form
   [:div.form-control
    [:label.control-label "URL"]
    [:input.form-control
     {:value (:src pane) :on-change #(update-pane (assoc pane :src (-> % .-target .-value)))}]]
   [:div.form-control
    ]])

(defn show-editor [model]
  (js/console.log "showing editor for " model)
  (reset! modal
          [:div.modal-dialog
           (content-editor model)]))

(defmulti content-view :content-type)

(defmulti pane-view :type)

(defmethod pane-view :content-pane [pane]
  [:div.fill.full
   {:on-click #(do
                 (reset! selected-pane-id (if (is-selected pane) nil (:id pane)))
                 false)
    :on-drag-enter #(-> % .-target .-classList (.add "drag-over") (and false))
    :on-drag-leave #(-> % .-target .-classList (.remove "drag-over") (and false))
    :on-drag-end #(js/console.log "drag ended...")
    :on-drop #(js/console.log "dropped") ;; #(show-editor (assoc pane :content-type (-> % .-dataTransfer (.getData "text") keyword)) on-change)
    :class (if (is-selected pane)  "selected-pane")}
   (content-view pane)])

(defmethod pane-view :container-pane [{:keys [pane1 pane2] :as opts}]
  [splitter opts
   (pane-view (pane-by-id pane1))
   (pane-view (pane-by-id pane2)) update-pane])

(defmethod content-view :image [{:keys [url fill? title]}]
  [:div
   (if title [:h4.top-relative title])
   [:img {:src url :class (if fill? "fill" "")}]])

(defmethod content-view :video [{:keys [url fill? title]}]
  [:video {:src url :class (if fill? "fill" "")}])

(defmethod content-view :default [pane]
  [:div.fill "blank content"])

(defn layout-editor []
  [:div.fill.full
   (if @alert [:div.alert.alert-fixed.alert-dismissible {:class (str "alert-" (first @alert))}
               [:button.close {:on-click #(reset! alert nil) :aria-label "Close"}
                [:span {:aria-hidden true} "×"]] (second @alert)])
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
    (reset! alert ["danger" "Please select the pane to split first"])))

(defn delete-pane []
  (if-let [pane-id @selected-pane-id]
    (let [parent-id (quot pane-id 10)
          sibling (-> @current-design :layout
                      (get (if (odd? pane-id) (inc pane-id) (dec pane-id))))]
      (update-pane (assoc sibling :id parent-id))
      (swap! current-design update :layout dissoc pane-id (inc pane-id) (dec pane-id))
      (reset! selected-pane-id nil))
    (reset! alert ["danger" "Please select the pane to split first"])))

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
              {:draggable true :on-drag-start (drag-start type)} label]))]]

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
            [:button.btn.btn-default {:title "New Project"}
             [:i.glyphicon.glyphicon-file] "New"]
            [:button.btn.btn-default {:title "Save Project"}
             [:i.glyphicon.glyphicon-save] "Save"]
            [:button.btn.btn-default {:title "Preview Project"}
             [:i.glyphicon-glyphicon-facetime-video] "Preview"]]]]]
        [:div.row.fill {:style {:padding-bottom "60px"}}
         [:div.col-md-12.fill.full {:style {:background-color "#f5f5f5"}}
          [:div#layoutBox.fill
           [layout-editor]]]]]])))
