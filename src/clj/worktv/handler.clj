(ns worktv.handler
  (:require [buddy.sign.jws :as jwt]
            [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [compojure.core :refer [context defroutes GET POST PUT]]
            [compojure.route :refer [resources]]
            [config.core :refer [env]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [html5 include-css include-js]]
            [postal.core :as postal]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [redirect set-cookie status not-found]]
            [worktv.db :as db]
            [worktv.middleware :refer [wrap-middleware]]))

(def JWT_COOKIE "auth-token")

(def active-users (atom #{}))

(defn valid-token? [token]
  (try
    (let [id (-> (jwt/unsign token (env :jwt-secret))
                 (String.)
                 (Integer/parseInt))]
      (if (@active-users id) id
          (when (db/find-user {:id id})
            (swap! active-users conj id)
            id)))
    (catch Exception e
      (log/warn "invalid token " e token))))

(defn wrap-auth [handler]
  (fn [req]
    (if-let [id (some-> req :cookies (get JWT_COOKIE) :value valid-token?)]
      (handler (assoc req :user-id id))
      (if (re-matches #"/login|/api/login|/api/verify" (:uri req))
        (handler req)
        (if (.startsWith (:uri req) "/api/")
          (status {:error "Authentication failed"} 403)
          (redirect "/login"))))))

(def mount-target
  [:div#app.fill])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))
   (include-css (if (env :dev) "/css/splitter.css" "/css/splitter.min.css"))
   (include-css "https://cdn.quilljs.com/1.3.6/quill.snow.css")
   (include-css "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css")
   ;; (include-css "css/assets.css")
   ;; (include-css "/css/bootstrap.min.css")
   (include-css "https://use.fontawesome.com/releases/v5.0.13/css/all.css")
   (include-js "https://ajax.googleapis.com/ajax/libs/jquery/1.12.4/jquery.min.js")
   (include-js "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js")
   (include-js "https://cdnjs.cloudflare.com/ajax/libs/handlebars.js/4.0.10/handlebars.min.js")
   ;; (include-js "https://www.gstatic.com/firebasejs/3.6.2/firebase-app.js")
   ;; (include-js "https://www.gstatic.com/firebasejs/3.6.2/firebase-auth.js")
   ;; (include-js "https://www.gstatic.com/firebasejs/3.6.2/firebase-database.js")
   (include-js "https://www.gstatic.com/charts/loader.js")
   (include-js "https://cdn.quilljs.com/1.3.6/quill.min.js")
   (include-js "https://cdnjs.cloudflare.com/ajax/libs/ace/1.4.5/ace.js")
   ])


(defn loading-page []
  (html5
   (head)
   [:body
    mount-target
    (include-js "/cljs-out/dev-main.js")]))

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

(def api-routes
  (context "/api" []
           (POST "/projects" []
                 (fn [req]
                   {:body (db/create-project (assoc (:body-params req) :owner (:user-id req)))}))

           (GET "/projects" []
                (fn [req]
                  {:body (db/find-projects (:params req))}))

           (GET "/projects/:project-id" [project-id]
                (fn [req]
                  {:body (db/get-project project-id)}))

           (PUT "/projects/:project-id" [project-id]
                (fn [req]
                  (log/info "handling project update")
                  (when (= 1
                           (db/update-project
                            project-id (assoc (:body-params req) :owner (:user-id req))))
                    (status {} 204))))

           (POST "/publish/:project-id/:pub-name" [project-id pub-name]
                 (fn [_]
                   (let [{:keys [error]} (db/publish-project project-id pub-name)]
                     (if error
                       (status {:body {:error (str "Failed publishing project. " error)}} 400)
                       (status {} 204)))))

           (GET "/published/:pub-name" [pub-name]
                (fn [_]
                  (log/info "get pub" pub-name)
                  (if-let [{proj-id :project_id} (db/get-published pub-name)]
                    {:body (db/get-project proj-id)}
                    (not-found {:error (str "No published project with name " pub-name)}))))

           (GET "/search" []
                (fn [req] (let [{:keys [q type]} (-> req :params)
                                result (search-images q type)]
                            {:body result})))

           (POST "/login" []
                 (fn[req]
                   (let [{:keys [email expiry] :as body} (:body-params req)
                         base-url (env :base-url (str (-> req :scheme name) "://"
                                                      (-> req :headers (get "host"))))
                         token (db/login-request body)
                         link (str base-url "/api/verify?token=" token)]
                     (log/info "login link:" link)
                     (postal/send-message
                      (env :smtp)
                      {:from (env :mail-from)
                       :to email :subject "TeamTV - Login Token"
                       :body
                       [{:type "text/html"
                         :content
                         (html [:p
                                [:h1 "Hi there"]
                                [:p "Here's your requested one time link to access TeamTV!"]
                                [:a {:href link} link]
                                [:p [:i "TeamTV"]]])}]})
                     (status {} 204))))

           (GET "/verify" []
                (fn[req]
                  (log/info "verify req:" req)
                  (let [token (-> req :params :token)
                        user (db/verify-token token)]
                    (log/info "user found?" user)
                    (if user
                      (-> (redirect "/")
                          (set-cookie JWT_COOKIE
                                      (jwt/sign (str (:id user)) (env :jwt-secret))
                                      {:max-age (* 60 60 24 60)
                                       :http-only true
                                       :path "/"}))
                      (-> (redirect "/login"))))))
           (GET "/user" []
                (fn [req]
                  (if-let [user (db/find-user {:id (:user-id req)})]
                    {:body (select-keys user [:email])}
                    (not-found {:error "user not found"}))))))

(defroutes routes
  (resources "/")

  (-> api-routes
      wrap-restful-format
      ;; (wrap-json-body {:keywords? true})
      ;; wrap-json-response
      wrap-keyword-params
      wrap-params
      wrap-auth
      wrap-cookies)

  (-> (context "/" []
               (GET "/*" [] (loading-page)))
      wrap-auth wrap-middleware)

  (not-found "Not Found"))

(def app (-> #'routes))
