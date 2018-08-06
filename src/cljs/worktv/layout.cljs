(ns worktv.layout
  (:require [ajax.core :refer [GET]]
            [cljs.core.async :refer [<! chan]]
            [cljs.reader :refer [read-string]]
            [commons-ui.core :as c]
            [reagent.core :as r :refer [atom] :refer-macros [with-let]]
            [reagent.session :as session]
            [secretary.core :as secretary]
            [worktv.backend :as b]
            [worktv.utils :as u]
            [worktv.splitter :refer [splitter]]
            [worktv.views
             :as
             v
             :refer
             [chart-form modal modal-dialog save-form search-project-form]]
            [cljsjs.mustache]
            [worktv.views :refer [web-page-form]]
            [worktv.utils :refer [handle-keys]]
            [worktv.views :refer [slides-form]]
            [re-frame.core :refer [subscribe]]
            [worktv.subs :refer [init-subs]]
            [worktv.events :refer [init-events]]
            [re-frame.core :refer [dispatch]]
            [worktv.db :as db]
            [worktv.utils :refer [event-no-default]]
            [worktv.views :refer [html-form]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

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
  (js/console.log "showing editor for" (clj->js pane))
  (when (:content-type pane)
    (dispatch [:modal (editor-dialog pane)])))

(def default-contents
  {:slides {:slides [{:layout {1 {:id 1 :type :content-pane}}}]}})

(defmulti content-view :content-type)

(defmulti pane-view :type)

(defmethod pane-view :content-pane [pane]
  [:div.fill.full
   (when *edit-mode*
     {:on-click #(dispatch [:select-pane (:id pane)])
      :on-double-click (event-no-default #(show-editor pane))
      :on-drag-over #(.preventDefault %)
      :on-drag-enter #(-> % .-target .-classList (.add "drag-over") (and false))
      :on-drag-leave #(-> % .-target .-classList (.remove "drag-over") (and false))
      :on-drag-end #(-> % .-target .-classList (.remove "drag-over") (and false))
      :on-drop #(do (show-editor
                     (assoc pane :content-type (-> % .-dataTransfer (.getData "text/plain") keyword)))
                    (-> % .-target .-classList (.remove "drag-over"))
                    false)
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

(defn data-view [url template refresh]
  (with-let [data (atom nil)
             load (fn []
                    (GET url :handler #(reset! data %) :response-format :json
                         :error-handler #(js/console.log "failed fetching " url ";" %)))
             _ (load)]
    (js/setTimeout load (* (Math/max 60 refresh) 1000))
    [:div.fit {:style {:overflow "hidden"} :dangerouslySetInnerHTML
               ;; {:__html (js/Mustache.render template (clj->js (or @data #js {})))}
               {:__html ((js/Handlebars.compile template) (clj->js (or @data #js {})))}}]))

(defmethod content-view :custom [{:keys [url template title refresh-interval]}]
  (js/console.log "custom:" url)
  (if (and url template)
    [data-view url template refresh-interval]
    [:div.fit "Missing url and/or template"]))

(defmethod content-view :chart [pane]
  [v/chart-view pane])

(defmethod content-view :default [pane]
  [:div.fill "blank content"])

(defmethod content-view :slides [pane]
  [:div.carousel-slide
   [:ol.carousel-indicators
    (for [i (range (:slide-count pane))]
      ^{:key i}[:li {:data-slide-to i}])
    [:li {:on-click #(js/console.log "add slide")} [:i.fa.fa-plus-circle]]]
   (for [slide (:slides pane)] ^{:key (:id slide)}
     [:div.carousel-inner
      [:div (pane-view slide)]])])

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
  ;; show prompt for publishing path
)
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
       (for [{:keys [type label icon]} db/content-types]
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
    (if @current-design
      [:div.preview
       {:on-key-press #(if (= 27 (u/visit (.-keyCode %) js/console.log)) (js/console.log "back!"))}
       [:div.fill.full
        (pane-view (pane-by-id 1))]])))
