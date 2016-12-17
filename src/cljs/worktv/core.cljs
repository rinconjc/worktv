(ns worktv.core
  (:require [accountant.core :as accountant]
            [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [worktv.layout :refer [delete-pane layout-editor split-pane]]))

;; -------------------------
;; Views
(defn menu-bar []
  [:nav.navbar.navbar-inverse.navbar-fixed-top
   [:div.container
    [:div.navbar-header
     [:button.navbar-toggle.collapsed {:data-toggle "collapse" :data-target "#navbar"
                                       :aria-expanded false :aria-controls "navbar"}
      [:span.sr-only "Toggle navigation"]
      [:span.icon-bar] [:span.icon-bar] [:span.icon-bar]]
     [:a.navbar-brand "Work TV"]]
    [:navbar.navbar-collapse-collapse
     [:ul.nav.navbar-nav
      [:li.dropdown
       [:a.dropdown-toggle {:data-toggle "dropdown" :role "button" :aria-haspopup true
                            :aria-expanded false} "Project" [:span.caret]]
       [:ul.dropdown-menu
        [:li [:a {:href "/project" } "New"]]
        [:li [:a "Open..."]]]]]]]])

(defn home-page []
  [:div [:h2 "Welcome to worktv"]
   [:div [:a {:href "/project"} "Design a presentation"]]])

(defn design-page []
  (let [model (atom {:layout {:type :content-pane} :screen "1280x720"})
        drag-start (fn [type] #(-> % .-dataTransfer (.setData "text/plain" type)))]
    (fn []
      [:div.container-fluid.fill
       [:div.row-fluid.fill
        [:div.col-md-2.fill
         [:h2 "Dashboard Designer"]
         [:div.panel.panel-default
          [:div.panel-heading "Widgets"]
          [:div.list-group
           [:button.list-group-item
            {:draggable true :on-dragstart (drag-start :image)} "Image"]
           [:button.list-group-item
            {:draggable true :on-dragstart (drag-start :video)} "Video"]
           [:button.list-group-item
            {:draggable true :on-dragstart (drag-start :table)} "Table"]
           [:button.list-group-item
            {:draggable true :on-dragstart (drag-start :chart)} "Chart"]
           [:button.list-group-item
            {:draggable true :on-dragstart (drag-start :slide)} "Slide"]]]]

        [:div.col-md-10.fill
         [:div.row
          [:div.col-md12.fill {:style {:padding "10px"}}
           [:div.form-inline
            [:label.form-label "Screen size"]
            [:input.form-control {:value (:screen @model)
                                  :on-change #(swap! model assoc :screen (-> % .-target .-value))}]
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
            [layout-editor (:layout @model) #(swap! model assoc :layout %)]]]]]]])))

(defn about-page []
  [:div [:h2 "About worktv"]
   [:div [:a {:href "/"} "go to the home page"]]])

(defn current-page []
  [:div.container-fluid.fill
   ;; [menu-bar]
   [(session/get :current-page)]])

;; -------------------------
;; Routes

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/project" []
  (session/put! :current-page #'design-page))

(secretary/defroute "/about" []
  (session/put! :current-page #'about-page))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (secretary/dispatch! path))
    :path-exists?
    (fn [path]
      (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))
