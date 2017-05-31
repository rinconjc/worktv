(ns worktv.core
  (:require [accountant.core :as accountant]
            [clojure.core.async :refer [<!]]
            [commons-ui.core :as c]
            [reagent.core :as reagent :refer [atom] :refer-macros [with-let]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [worktv.backend :as b]
            [worktv.layout :as l :refer [design-page preview-page]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; -------------------------
;; Views
(defn menu-bar []
  [:nav.navbar.navbar-default
   [:div.container-fluid
    [:div.navbar-header
     [:button.navbar-toggle.collapsed {:data-toggle "collapse" :data-target "#navbar"
                                       :aria-expanded false :aria-controls "navbar"}
      [:span.sr-only "Toggle navigation"]
      [:span.icon-bar] [:span.icon-bar] [:span.icon-bar]]
     [:a.navbar-brand "Dash" [:i "It"]]]
    [:navbar.navbar-collapse-collapse {:id "navbar"}
     [:ul.nav.navbar-nav.navbar-left
      (if (session/get :user)
        [:li [:a {:href "/project"} "Design"]])]
     [:ul.nav.navbar-nav.navbar-right
      (if (session/get :user)
        [:li [:a {:href "/logout"} "Logout"]]
        [:li [:a {:href "/login"} "Login"]])]]]])

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
     [:div.col-sm-3.col-sm-offset-4
      [:h2 "Login"]
      [:div.row @error]
      [:form.form
       {:on-submit #(do  (.preventDefault %)
                         (go
                           (let [[user, err] (<! (b/login (:username @login) (:password @login)))]
                             (if err
                               (reset! error [c/alert "danger" err])
                               (do (session/put! :user user) (secretary/dispatch! "/"))))))}
       [c/input {:type "email" :label "Email:" :model [login :username]}]
       [c/input {:type "password" :label "Password:" :model [login :password]}]
       [:button.btn.btn-primary "Login"]]]]))

(defn current-page []
  (let [[page menu-bar] (as-> (session/get :current-page) p
                          (if-not (vector? p) [p menu-bar] p))]
    [:div.container-fluid.fill.full
     (if menu-bar [menu-bar])
     [:div.row-fluid.fill.full
      [page]]]))

;; -------------------------
;; Routes

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/project" []
  (session/put! :current-page [#'l/design-page #'l/menu-bar]))

(secretary/defroute "/login" []
  (session/put! :current-page #'login-page))

(secretary/defroute "/logout" []
  (session/remove! :user)
  (session/put! :current-page #'home-page))

(secretary/defroute "/about" []
  (session/put! :current-page #'about-page))

(secretary/defroute "/preview" []
  (session/put! :current-page [#'preview-page nil]))

(secretary/defroute "/show/:folder/:proj-id" [folder proj-id]
  (session/put! :current-page #'preview-page)
  (l/load-project (str folder "/" proj-id)))


;; -------------------------
;; Initialize app
(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )

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
  (mount-root)
  (js/Handlebars registerHelper "fmt" #()))
