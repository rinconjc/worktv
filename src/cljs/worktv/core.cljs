(ns worktv.core
  (:require [accountant.core :as accountant]
            [reagent.core :as reagent]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [worktv.layout :refer [design-page]]))

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
