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

(defn default-menu []
  [:nav.navbar-collapse-collapse {:id "navbar"}
   [:ul.nav.navbar-nav.navbar-left
    (if (session/get :user)
      [:li [:a {:href "/project"} "Design"]])]
   [:ul.nav.navbar-nav.navbar-right
    (if (session/get :user)
      [:li [:a {:href "/logout"} "Logout"]]
      [:li [:a {:href "/login"} "Login"]])]])

(defn menu-bar [page-menu]
  [:nav.navbar.navbar-default
   [:div.container-fluid
    [:div.navbar-header
     [:button.navbar-toggle.collapsed {:data-toggle "collapse" :data-target "#navbar"
                                       :aria-expanded false :aria-controls "navbar"}
      [:span.sr-only "Toggle navigation"]
      [:span.icon-bar] [:span.icon-bar] [:span.icon-bar]]
     [:a.navbar-brand "TeamTv"]]
    [page-menu]]])

(defn home-page []
  [:div [:h2 "Welcome to TeamTv"]
   [:div [:a {:href "/project"} "Design a presentation"]]])

(defn about-page []
  [:div [:h2 "About TeamTv"]
   [:div [:a {:href "/"} "go to the home page"]]])

(defn login-confirm-page []
  [:div [:h2 "Login"]
   [:div "A temporary password and link has been sent to your mailbox, please verify your email."]])

(defn login-page []
  (with-let [login (atom nil)
             error (atom nil)]
    [:div.row
     [:div.col-sm-3.col-sm-offset-4
      [:h2 "Login"]
      [:div.row @error]
      [:form.form
       {:on-submit
        #(b/login-with-email (:username @login))}
       [c/input {:type "email" :label "Email:" :model [login :username]}]
       ;; [c/input {:type "password" :label "Password:" :model [login :password]}]
       [:button.btn.btn-primary "Login"]]]]))

(defn current-page []
  (let [[page page-menu] (as-> (session/get :current-page) p
                           (if-not (vector? p) [(or p #'home-page) default-menu] p))]
    (js/console.log "page?" (nil? page) " page-menu?" (nil? page-menu))
    [:div.container-fluid.fill.full
     [menu-bar page-menu]
     [:div.row-fluid.fill.full
      [page]]]))

;; -------------------------
;; Routes

(secretary/defroute "/" []
  (js/console.log "route /")
  (session/put! :current-page #'home-page))

(secretary/defroute "/project" []
  (if (session/get :user)
    (session/put! :current-page [#'l/design-page #'l/design-menu])
    (accountant/navigate! "/")))

(secretary/defroute "/login" []
  (session/put! :current-page #'login-page))

(secretary/defroute "/login-confirm" []
  (session/put! :current-page #'login-confirm-page))

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
  (js/Handlebars.registerHelper
   "round" (fn [value opts]
             (-> value js/Number (.toFixed (-> opts .-hash .-decimals))))))
