(ns worktv.handler
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [compojure.core :refer [context defroutes GET POST]]
            [compojure.route :refer [not-found resources]]
            [config.core :refer [env]]
            [hiccup.page :refer [html5 include-css include-js]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.util.response :refer [redirect]]
            [worktv.middleware :refer [wrap-middleware]]))

(defn valid-token? [token]
  true)

(defn wrap-auth [handler]
  (fn [req]
    (if (some-> req :cookies "token" :value valid-token?)
      (handler req)
      (redirect "/login"))))

(def mount-target
  [:div#app.fill
   (if (env :dev)
     [:h3 "ClojureScript has not been compiled!"]
     [:p "please run "
      [:b "lein figwheel"]
      " in order to start the compiler"])])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))
   (include-css (if (env :dev) "/css/splitter.css" "/css/splitter.min.css"))
   ;; (include-css "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.4/css/bootstrap.min.css")
   (include-css "/css/bootstrap.min.css")
   (include-css "https://use.fontawesome.com/releases/v5.0.13/css/all.css")
   (include-js "https://ajax.googleapis.com/ajax/libs/jquery/1.12.4/jquery.min.js")
   (include-js "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js")
   (include-js "https://cdnjs.cloudflare.com/ajax/libs/handlebars.js/4.0.10/handlebars.min.js")
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
  (spit "/tmp/dump.html" html)
  (let [urls (into {} (for [[_ meta] (re-seq #"class=\"rg_meta[^\"]*\">\{([^\}]+)\}" html)]
                        (-> (re-find #"\"id\":\"([^\"]+)\".+\"ou\":\"([^\"]+)\"" meta) rest vec)))]
    (println "urls:" urls)
    (for [[_ id img] (re-seq #"\[\"([^\"]+)\",\"(data:image[^\"]+)\"\]" html)
          :let [i (str/index-of img "\\u003d")]]
      {:url (urls id id) :image (if i (.substring img 0 i) img)})))

(defn search-images [q type]
  (-> (client/get "https://www.google.com.au/search"
                  {:query-params {"q" q "tbm" "isch"}
                   :insecure? true
                   :headers {"User-Agent" "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36"}})
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
  (->  (context "/" []
                (GET "/*" [] (loading-page)))
       wrap-auth
       wrap-csrf-cookie)

  (resources "/")
  (not-found "Not Found"))

(def app (wrap-middleware #'routes))
