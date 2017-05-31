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
            [cljsjs.mustache])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def ^:dynamic *edit-mode* true)

(def content-types [{:type :image :label "Image" :icon "fa-image"}
                    {:type :video :label "Video" :icon "fa-file-video-o"}
                    {:type :custom :label "Custom" :icon "fa-code"}
                    {:type :chart :label "Chart" :icon "fa-bar-chart-o"}])

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
   (if *edit-mode*
     {:on-click #(do
                   (reset! selected-pane-id (if (is-selected pane) nil (:id pane)))
                   false)
      :on-double-click #(do (.preventDefault %) (show-editor pane))
      :on-drag-over #(.preventDefault %)
      :on-drag-enter #(-> % .-target .-classList (.add "drag-over") (and false))
      :on-drag-leave #(-> % .-target .-classList (.remove "drag-over") (and false))
      :on-drag-end #(-> % .-target .-classList (.remove "drag-over") (and false))
      :on-drop #(show-editor
                 (update-pane (assoc pane :content-type (-> % .-dataTransfer (.getData "text/plain") keyword))))
      :class (if (is-selected pane)  "selected-pane")})
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
             pane1-id (merge pane {:id pane1-id :type :content-pane})
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

(defn load-project
  ([path] (go (let [[data error] (<! (b/get-project path))]
                (if data (load-project nil data)))))
  ([key proj-data]
   (if-not proj-data {:error "Select a project!"}
           (reset! current-design
                   (-> proj-data
                       (js->clj :keywordize-keys true)
                       (update :layout read-string)
                       (assoc :id key))))))

(defn handle-open-project []
  (go
    (let [projs (<! (b/find-projects (.-uid (session/get :user))))
          selection (atom nil)]
      (reset! modal [modal-dialog {:title "Open a design..."
                                   :ok-fn #(go (apply load-project @selection))}
                     [search-project-form projs selection]]))))

(defn do-publish-project []
  (go
    (let [[result error] (<! (b/publish-project (.-uid (session/get :user)) @current-design))]
      (if result
        (secretary/dispatch! (str "/show/" (:folder @current-design) "/" (:id @current-design)))
        (reset! alert [c/alert {:type "danger"} (str "Failed publishing:" error)])))))

(defn menu-bar []
  [:nav.navbar.navbar-default
   [:div.container-fluid
    [:div.navbar-header
     [:button.navbar-toggle.collapsed {:data-toggle "collapse" :data-target "#navbar"
                                       :aria-expanded false :aria-controls "navbar"}
      [:span.sr-only "Toggle navigation"]
      [:span.icon-bar] [:span.icon-bar] [:span.icon-bar]]
     [:a.navbar-brand "Dash" [:i "It"]]]
    [:navbar.navbar-collapse-collapse
     [:ul.nav.navbar-nav
      [:li [:a {:href "/"} "Home"]]
      [:li.dropdown
       [:a.dropdown-toggle {:data-toggle "dropdown" :role "button" :aria-haspopup true
                            :aria-expanded false} "Project" [:span.caret]]
       [:ul.dropdown-menu
        [:li [:a {:href "#" :title "Open Project" :on-click handle-open-project}
              "Open"]]
        [:li [:a {:href "#" :title "New Project"
                  :on-click #(reset! current-design blank-design)}
              "New"]]
        [:li [:a {:href "#" :title "Save Project" :on-click handle-save-project}
              "Save"]]
        [:li [:a {:href "/preview" :title "Preview Project"}
              "Preview"]]
        [:li [:a {:href "#" :title "Publish Project"
                  :on-click #(do (do-publish-project) (.preventDefault %))}
              "Publish"]]
        [:li [:a {:href "#" :title "Show Project"
                  :on-click #(do (secretary/dispatch! (str "/show/" (:name @current-design)))
                                 (.preventDefault %))}
              "Show"]]]]
      [:li
       [:div.btn-toolbar
        [:span.navbar-text "|"]
        [:div.btn-group
         [:button.btn.btn-default.navbar-btn.disabled "Layout"]
         [:button.btn.btn-default.navbar-btn
          {:on-click #(split-pane :vertical) :title "Split pane vertically"}
          [:i.fa.fa-columns.fa-fw.fa-rotate-270] "Vertical"]
         [:button.btn.btn-default.navbar-btn
          {:on-click #(split-pane :horizontal) :title "Split pane horizontally"}
          [:i.fa.fa-columns.fa-fw] "Horizontal"]
         [:button.btn.btn-default.navbar-btn
          {:on-click #(delete-pane) :title "Delete selected pane"}
          [:i.fa.fa-trash-o.fa-fw] "Delete"]]
        [:span.navbar-text "|"]
        [:div.btn-group
         [:button.btn.btn-default.navbar-btn.disabled "Widgets"]
         (doall
          (for [{:keys [type label icon]} content-types]
            ^{:key type}
            [:button.btn.btn-default.navbar-btn
             {:draggable true :on-drag-start #(-> % .-dataTransfer (.setData "text/plain" (name type)))
              :title label}
             [:i.fa.fa {:class icon}]]))]]]]]]])

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
