(ns worktv.layout
  (:require [ajax.core :refer [GET]]
            [commons-ui.core :as c]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r :refer [atom] :refer-macros [with-let]]
            [secretary.core :as secretary]
            [worktv.db :as db]
            [worktv.events :refer [init-events]]
            [worktv.splitter :refer [splitter]]
            [worktv.subs :refer [init-subs]]
            [worktv.utils :as u :refer [event-no-default handle-keys]]
            [worktv.views
             :as
             v
             :refer
             [chart-form
              html-form
              modal-dialog
              save-form
              search-project-form
              slides-form
              web-page-form]]))

(init-subs)
(init-events)

(def ^:dynamic *edit-mode* true)


(def current-design (subscribe [:current-project]))
;; (def current-design
;;   (let [model (session/cursor [:current-design])]
;;     (when-not @model
;;       (session/put! :current-design blank-design))
;;     model))

;; (def selected-pane-id (atom nil))
;; (def alert (atom nil))


(defn data-from [url refresh-rate]
  (let [data (atom nil)]
    (GET url :handler #(reset! data %) :error-handler #(js/console.log "failed retrieving " url (clj->js %)))
    data))

(defn pane-by-id [id]
  (-> @current-design :layout (get id)))

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

(defmethod content-editor :page [pane]
  [web-page-form pane])

(defmethod content-editor :slides [pane]
  [slides-form pane])

(defmethod content-editor :html [pane]
  [html-form pane])

(defn editor-dialog [pane]
  (with-let [model (atom pane)]
    {:title (str "Edit " (-> @model :content-type name) " details")
     :ok-fn #(dispatch [:update-pane @model])
     :content (content-editor model)}))

(defn show-editor [pane]
  (when (:content-type pane)
    (dispatch [:modal (editor-dialog pane)])))

(def default-contents
  {:slides {:slides [{:layout {1 {:id 1 :type :content-pane}}}]}})

(defmulti content-view :content-type)

(defmulti pane-view :type)

(defmethod pane-view :content-pane [pane]
  [:div.full
   (when *edit-mode*
     {:on-click #(dispatch [:select-pane (:id pane)])
      :on-double-click (event-no-default #(show-editor pane))
      :on-drag-over #(.preventDefault %)
      :on-drag-enter #(-> % .-target .-classList (.add "drag-over") (and false))
      :on-drag-leave #(-> % .-target .-classList (.remove "drag-over") (and false))
      :on-drag-end #(-> % .-target .-classList (.remove "drag-over") (and false))
      :on-drop (event-no-default
                #(do (show-editor
                      (merge pane (db/default-content
                                   (-> % .-dataTransfer (.getData "text/plain") keyword))))
                     (-> % .-target .-classList (.remove "drag-over"))))
      :class (when (= (:id pane) @(subscribe [:selected-pane-id]))  "selected-pane")})
   (content-view pane)])

(defmethod pane-view :container-pane [{:keys [pane1 pane2] :as opts}]
  [splitter opts
   (pane-view (pane-by-id pane1))
   (pane-view (pane-by-id pane2)) #(dispatch [:update-pane %])])

(defmethod content-view :image [{:keys [url display title]}]
  [:div.full.fill
   (if title [:h4.top-relative title])
   [:img.fit {:src url :class display}]])

(defmethod content-view :video [{:keys [url fill? title]}]
  (if (and url (re-find #"youtube.com" url))
    [:div.fill.full {:style {:padding "40px"}}
     [:iframe {:frame-border 0 :src url :style {:margin "5px" :width "100%" :height "100%"}} ]]
    [:video {:src url :class (if fill? "fill" "")}]))

(defn data-view [id]
  (with-let [data (subscribe [:content-data id])]
    [:div.fit {:style {:overflow "hidden"} :dangerouslySetInnerHTML
               ;; {:__html (js/Mustache.render template (clj->js (or @data #js {})))}
               {:__html @data}}]))

(defmethod content-view :custom [{:keys [url template title refresh-interval]}]
  (if (and url template)
    [data-view url template refresh-interval]
    [:div.fit "Missing url and/or template"]))

(defmethod content-view :chart [pane]
  [v/chart-view pane])

(defmethod content-view :default [pane]
  [:div.fill "blank content"])

(defmethod content-view :slides [{:keys [active slides] :or {active 0} :as pane}]
  [:div.carousel-slide.full
   [:ol.carousel-indicators
    (for [i (range (count (:slides pane)))]
      ^{:key i}[:li {:data-slide-to i}])]
   [:div.carousel-inner.full
    (doall
     (map-indexed
      (fn[i slide] ^{:key i}
        [:div.item.full (when (= active i) {:class "active"})
         [:div.full (pane-view slide)]]) (:slides pane)))]

   [:div {:style {:position "absolute" :bottom "10px" :width "100%" :text-align "center"}}
    (when *edit-mode*
      [:div.btn-group
       (when (pos? active)
         [:button.btn.btn-default {:on-click #(dispatch [:slide-active pane (dec active)])}
          [:span.glyphicon.glyphicon-chevron-left]])
       (when (< active (dec (count (:slides pane))))
         [:button.btn.btn-default {:on-click #(dispatch [:slide-active pane (inc active)])}
          [:span.glyphicon.glyphicon-chevron-right]])
       [:button.btn.btn-default
        {:on-click #(show-editor (assoc (get-in pane [:slides active])
                                        :path [(:id pane) :slides active]))}
        [:span.glyphicon.glyphicon-edit]]
       [:div.btn-group
        [:button.btn.btn-default.dropdown-toggle
         {:data-toggle "dropdown" :aria-haspopup "true" :aria-expanded "false"}
         [:span.glyphicon.glyphicon-plus] [:span.caret]]
        [:ul.dropdown-menu
         (for [[type {:keys [label icon]}] db/slide-content-types]
           ^{:key type}
           [:li [:a {:href "#" :title label
                     :on-click #(show-editor
                                 (assoc (db/default-content type)
                                        :type :content-pane
                                        :path [(:id pane) :slides (or (count (:slides pane)) 0)]))}
                 [:i.fa.fa-fw {:class icon}] label]])]]
       [:button.btn.btn-default {:on-click #(dispatch [:delete-slide pane active])}
        [:span.glyphicon.glyphicon-minus]]])]])

(defmethod content-view :html [pane]
  [:div {:dangerouslySetInnerHTML {:__html (:content pane)}}])

(defn alert [attrs]
  (when-not (empty? (:text attrs))
    [c/alert-box (assoc attrs :on-close #(dispatch [:hide-alert]))
     (:text attrs)]))

(defn layout-editor []
  [:div.fill.full
   {:tabIndex 1
    :on-key-down (handle-keys "ctrl+h" #(dispatch [:split-pane :horizontal])
                              "ctrl+v" #(dispatch [:split-pane :vertical])
                              "ctrl+k" #(dispatch [:delete-pane]))}
   [alert @(subscribe [:alert])]
   [modal-dialog]
   (when @current-design
     (pane-view (pane-by-id 1)))])

(defn handle-save-project []
  (if (:id @current-design)
    (dispatch [:save-project])
    (let [data (atom {})]
      (dispatch [:modal {:title "Save Design"
                         :content [save-form data]
                         :ok-fn #(dispatch [:save-project @data])}]))))

(defn handle-open-project []
  (dispatch [:find-projects "%"])
  (dispatch [:modal {:title "Open Project"
                     :ok-event [:open-project]
                     :content [search-project-form]}]))

(defn handle-publish-project []
  (let [data (atom {})]
    (dispatch [:modal {:title "Publish Project"
                       :ok-event [:publish-project @data]
                       :content [v/publish-form data]}])))
;; (defn do-publish-project []
;;   (go
;;     (let [[result error] (<! (b/publish-project (.-uid (session/get :user)) @current-design))]
;;       (if result
;;         (secretary/dispatch! (str "/show/" (:folder @current-design) "/" (:id @current-design)))
;;         (reset! alert [c/alert {:type "danger"} (str "Failed publishing:" error)])))))

(defn design-menu []
  [:nav.navbar-collapse-collapse
   [:ul.nav.navbar-nav
    [:li [:a {:href "/"} "Home"]]
    [:li.dropdown
     [:a.dropdown-toggle {:data-toggle "dropdown" :role "button" :aria-haspopup true
                          :aria-expanded false} "Project" [:span.caret]]
     [:ul.dropdown-menu
      [:li [:a {:href "#" :title "Open Project"
                :on-click handle-open-project}
            "Open"]]
      [:li [:a {:href "#" :title "New Project"
                :on-click #(dispatch [:design true])}
            "New"]]
      [:li [:a {:href "#" :title "Save Project" :on-click handle-save-project}
            "Save"]]
      [:li [:a {:href "/preview" :title "Preview Project"}
            "Preview"]]
      [:li [:a {:href "#" :title "Publish Project"
                :on-click handle-publish-project}
            "Publish"]]
      [:li [:a {:href "#" :title "Show Project"
                :on-click #(do (secretary/dispatch! (str "/show/" (:name @current-design)))
                               (.preventDefault %))}
            "Show"]]]]
    [:li.dropdown
     [:a.dropdown-toggle {:data-toggle "dropdown" :role "button" :aria-haspopup true
                          :aria-expanded false} "Layout" [:span.caret]]
     [:ul.dropdown-menu
      [:li [:a {:href "#" :on-click #(dispatch [:split-pane :vertical]) :title "Split pane vertically"}
            [:i.fa.fa-columns.fa-fw.fa-rotate-270] "Vertical"]]
      [:li [:a {:href "#" :on-click #(dispatch [:split-pane :horizontal]) :title "Split pane horizontally"}
            [:i.fa.fa-columns.fa-fw] "Horizontal"]]
      [:li [:a {:href "#" :on-click #(dispatch [:delete-pane]) :title "Delete selected pane"}
            [:i.fa.fa-trash.fa-fw] "Delete"]]]]
    [:li.dropdown
     [:a.dropdown-toggle {:data-toggle "dropdown" :role "button" :aria-haspopup true
                          :aria-expanded false} "Widgets" [:span.caret]]
     [:ul.dropdown-menu
      (doall
       (for [[type {:keys [label icon]}] db/content-types]
         ^{:key type}
         [:li [:a {:href "#" :draggable true
                   :on-drag-start #(-> % .-dataTransfer (.setData "text/plain" (name type)))
                   :title label}
               [:i.fa.fa-fw.fa {:class icon}] label]]))]]]])

(defn design-page []
  [:div.row.fill {:style {:padding "0px 20px 90px"}}
   [:div.col-md-12.fill.full {:style {:background-color "#f5f5f5"}}
    [:div#layoutBox.fill {:style {:border "solid 1px" :border-color "#ddd"}}
     [layout-editor]]]])

(defn preview-page []
  (binding [*edit-mode* false]
    (when @current-design
      (with-let []
        [:div.preview
         {:on-key-press #(if (= 27 (u/visit (.-keyCode %) js/console.log)) (js/console.log "back!"))}
         [:div.fill.full
          (pane-view (pane-by-id 1))]]
        (finally (dispatch [:stop-playing]))))))
