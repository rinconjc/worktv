(ns worktv.handler
  (:require [clj-http.client :as client]
            [compojure
             [core :refer [context defroutes GET POST]]
             [route :refer [not-found resources]]]
            [config.core :refer [env]]
            [hiccup.page :refer [html5 include-css include-js]]
            [ring.middleware
             [anti-forgery :refer [*anti-forgery-token*]]
             [json :refer [wrap-json-body wrap-json-response]]]
            [worktv.middleware :refer [wrap-middleware]]
            [clojure.string :as str]))

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
   ;; (include-js "https://www.gstatic.com/firebasejs/3.6.2/firebase-app.js")
   ;; (include-js "https://www.gstatic.com/firebasejs/3.6.2/firebase-auth.js")
   ;; (include-js "https://www.gstatic.com/firebasejs/3.6.2/firebase-database.js")
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

(defn extract-urls-from-google-results [html]
  (let [urls (map second (re-seq #"\"ou\":\"([^\"]+)\"" html))
        imgs (for [[_ img] (re-seq #",\"(data:image[^\"]+)\"" html)
                   :let [i (str/index-of img "\\u003d")]]
               (if i (.substring img 0 i) img))]
    (map #(hash-map :image %1 :url %2) imgs urls)))

(defn search-images [q type]
  (-> (client/get "https://www.google.com.au/search"
                  {:query-params {"q" q "tbm" "isch"}
                   :headers {"User-Agent" "Mozilla/5.0 (X11; Linux i686; rv:10.0.1) Gecko/20100101 Firefox/10.0.1 SeaMonkey/2.7.1"}})
      :body extract-urls-from-google-results))

(defroutes routes
  (-> (context "/api" []
               (POST "/project" [req]
                     (println "req:" (-> req :body)))
               (GET "/search" []
                    (fn [req] (let [{:keys [q type]} (-> req :params)
                                    result (search-images q type)]
                                {:body result}))))
      wrap-json-body wrap-json-response)
  (wrap-csrf-cookie
   (context "/" []
            (GET "/*" [] (loading-page))))                 ;

  (resources "/")
  (not-found "Not Found"))

(def app (wrap-middleware #'routes))
