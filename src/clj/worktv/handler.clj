(ns worktv.handler
  (:require [compojure
             [core :refer [context defroutes GET POST]]
             [route :refer [not-found resources]]]
            [config.core :refer [env]]
            [hiccup.page :refer [html5 include-css include-js]]
            [ring.middleware
             [anti-forgery :refer [*anti-forgery-token*]]
             [json :refer [wrap-json-body]]]
            [worktv.middleware :refer [wrap-middleware]]))

(def mount-target
  [:div#app.fill
   [:h3 "ClojureScript has not been compiled!"]
   [:p "please run "
    [:b "lein figwheel"]
    " in order to start the compiler"]])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))
   (include-css (if (env :dev) "/css/splitter.css" "/css/splitter.min.css"))
   (include-css "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.4/css/bootstrap.min.css")
   (include-css "//maxcdn.bootstrapcdn.com/font-awesome/4.1.0/css/font-awesome.min.css")
   (include-js "https://ajax.googleapis.com/ajax/libs/jquery/1.12.4/jquery.min.js")
   (include-js "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js")
   (include-js "https://www.gstatic.com/firebasejs/3.6.2/firebase-app.js")
   (include-js "https://www.gstatic.com/firebasejs/3.6.2/firebase-auth.js")
   (include-js "https://www.gstatic.com/firebasejs/3.6.2/firebase-database.js")
   (include-js "https://www.gstatic.com/charts/loader.js")
   ])


(defn loading-page []
  (html5
   (head)
   [:body
    mount-target
    (include-js "/js/app.js")]))

(defn wrap-csrf-cookie [handler]
  (fn [request]
    (update (handler request) :cookies assoc :csrf-token {:value *anti-forgery-token*})))

(defroutes routes
  (wrap-csrf-cookie
   (context "/" []
            (GET "/*" [] (loading-page))))
  (wrap-json-body
   (context "/api" []
            (POST "/project" [req]
                  (println "req:" (-> req :body)))))

  (resources "/")
  (not-found "Not Found"))

(def app (wrap-middleware #'routes))
