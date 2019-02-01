(ns ^:figwheel-hooks worktv.core
  (:require [accountant.core :as accountant]
            [commons-ui.core :as c]
            [re-frame.core :refer [dispatch dispatch-sync subscribe]]
            [reagent.core :as reagent :refer [atom] :refer-macros [with-let]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [worktv.events :refer [init-events]]
            [worktv.layout :as l :refer [preview-page progress-page]]
            [worktv.subs :refer [init-subs]]
            [worktv.utils :refer [event-no-default handle-keys]]))

(init-events)
(init-subs)
;; -------------------------
;; Views

(defn default-menu []
  (with-let [user (subscribe [:user])]
    [:nav.navbar-collapse-collapse {:id "navbar"}
     [:ul.nav.navbar-nav.navbar-left
      (if @user
        [:li [:a {:href "#" :on-click #(dispatch [:design])} "Design"]])]
     [:ul.nav.navbar-nav.navbar-right
      (if @user
        [:li [:a {:href "#"} (:name @user)]]
        [:li [:a {:href "/login"} "Login"]])]]))

(defn menu-bar [page-menu]
  [:nav.navbar.navbar-inverse
   [:div.container-fluid
    [:div.navbar-header
     [:button.navbar-toggle.collapsed {:data-toggle "collapse" :data-target "#navbar"
                                       :aria-expanded false :aria-controls "navbar"}
      [:span.sr-only "Toggle navigation"]
      [:span.icon-bar] [:span.icon-bar] [:span.icon-bar]]
     [:a.navbar-brand "TeamTVi"]]
    [page-menu]]])

(defn home-page []
  [:div [:h2 "Welcome to TeamTVi"]
   [:div [:a {:href "#" :on-click #(dispatch [:design])} "Design a presentation"]]])

(defn about-page []
  [:div [:h2 "About TeamTVi"]
   [:div [:a {:href "/"} "go to the home page"]]])

(defn login-confirm-page []
  [:div [:h2 "Access Link Sent!"]
   [:div "A secure access link has been sent to your mailbox, please check your email."]])

(defn login-page []
  (with-let [login (atom nil)]
    [:div.row
     [:div.col-sm-5.col-sm-offset-4
      [:h2 "Login"]
      [:div.row (c/alert @(subscribe [:alert]))]
      [:form.form
       {:on-submit (event-no-default #(dispatch [:login-with-email (:username @login)]))}
       [c/input {:type "email" :label "Email:" :model [login :username]}]
       ;; [c/input {:type "password" :label "Password:" :model [login :password]}]
       [:button.btn.btn-primary "Login"]]]]))

(defn current-page []
  (let [[page page-menu] (as-> @(subscribe [:current-page]) p
                           (if-not (vector? p) [(or p #'home-page) default-menu] p))]
    [:div.container-fluid.full
     ;; {:on-key-down (handle-keys "esc" #(dispatch [:design]))}
     (when page-menu [menu-bar page-menu])
     [:div.row-fluid.full
      [page]]]))

;; -------------------------
;; Routes

(secretary/defroute "/" []
  (dispatch [:current-page #'home-page]))

(secretary/defroute "/project" []
  (if @(subscribe [:user])
    (dispatch [:current-page [#'l/design-page #'l/design-menu]])
    (accountant/navigate! "/")))

(secretary/defroute "/login" []
  (dispatch [:current-page #'login-page]))

(secretary/defroute "/login-confirm" []
  (dispatch [:current-page #'login-confirm-page]))

(secretary/defroute "/logout" []
  (session/remove! :user)
  (dispatch [:current-page #'home-page]))

(secretary/defroute "/about" []
  (dispatch [:current-page #'about-page]))

(secretary/defroute "/preview" []
  (js/console.log "previewing project")
  (dispatch [:start-playing])
  (dispatch [:current-page [#'preview-page nil]]))

(secretary/defroute "/show/:folder/:proj-id" [folder proj-id]
  (dispatch [:load-project proj-id])
  (dispatch [:current-page #'preview-page]))

(secretary/defroute "/view/:pub-name" [pub-name]
  (dispatch-sync [:current-page [#'progress-page nil]])
  (dispatch [:show-publishing pub-name [#'preview-page nil]]))

;; -------------------------
;; Initialize app
(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )

(defn ^:after-load mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (dispatch-sync [:init])
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
(init!)
