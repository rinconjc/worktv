(ns worktv.db
  (:require [clojure.java.jdbc :as jdbc]
            [hikari-cp.core :as cp]
            [ragtime.jdbc :as r]
            [ragtime.repl :as rr])
  (:import java.security.SecureRandom
           java.util.Base64
           java.lang.Integer))


(defn generate-token [id size]
  (let [bucket (byte-array size)]
    (.nextBytes (SecureRandom.) bucket)
    (.encodeToString (Base64/getUrlEncoder)
                     (byte-array (concat bucket (to-bytes id))))))

(def db-spec
  (delay
   (let [ds {:datasource
             (cp/make-datasource
              {:adapter "h2"
               :url (str "jdbc:h2:" (or (System/getProperty "DB_FILE") "~/teamtv-db"))})}]
     (rr/migrate {:datastore (r/sql-database ds)
               :migrations (r/load-resources "migrations")} )
     ds)))

(defn find-user [email]
  (first
   (jdbc/query @db-spec ["select * from app_users where email = ?" email])))

(defn login-request [login]
  (let [token (generate-token 40)
        user (find-user (:email login))]
    (-> (if user
       (jdbc/update! @db-spec "app_users" {:random_token token} ["id = ?" (:id user)])
       (jdbc/insert! @db-spec "app_users"
                     (-> login (select-keys [:email])
                         (assoc :status "REGISTERED" :random_token token))))
        first (merge login))))
