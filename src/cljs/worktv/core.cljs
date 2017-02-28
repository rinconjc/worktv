(ns worktv.core
  (:require [accountant.core :as accountant]
            [clojure.core.async :refer [<!]]
            [commons-ui.core :as c]
            [reagent.core :as reagent :refer [atom] :refer-macros [with-let]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [worktv.backend :as b]
            [worktv.layout :refer [design-page preview-page]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; -------------------------
;; Views
(defn menu-bar []
  [:nav.navbar.navbar-inverse.navbar-fixed-top
   [:div.container-fluid
    [:div.navbar-header
     [:button.navbar-toggle.collapsed {:data-toggle "collapse" :data-target "#navbar"
                                       :aria-expanded false :aria-controls "navbar"}
      [:span.sr-only "Toggle navigation"]
      [:span.icon-bar] [:span.icon-bar] [:span.icon-bar]]
     [:a.navbar-brand "Work TV"]]
    [:navbar.navbar-collapse-collapse {:id "navbar"}
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

(defn login-page []
  (with-let [login (atom nil)
             error (atom nil)]
    [:div.row
     [:div.col-md-3.col-md-offset-4
      [:h2 "Login"]
      @error
      [:form.form
       {:on-submit #(do  (.preventDefault %)
                         (go
                           (let [[user, err] (<! (b/login (:username @login) (:password @login)))]
                             (if err
                               (reset! error [c/alert "danger" err])
                               (do
                                 (session/put! :user user)
                                 (accountant/dispatch-current!))))))}
       [c/input {:type "text" :label "Email:" :model [login :username]}]
       [c/input {:type "password" :label "Password:" :model [login :password]}]
       [:button.btn.btn-primary "Login"]]]]))

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

(secretary/defroute "/login" []
  (session/put! :current-page #'login-page))

(secretary/defroute "/about" []
  (session/put! :current-page #'about-page))

(secretary/defroute "/preview" []
  (session/put! :current-page #'preview-page))

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
  (if-not (session/get :user)
    (secretary/dispatch! "/login")
    (accountant/dispatch-current!))
  (mount-root))
