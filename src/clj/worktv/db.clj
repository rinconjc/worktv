(ns worktv.db
  (:require [clojure.java.jdbc :as jdbc]
            [hikari-cp.core :as cp]
            [ragtime.jdbc :as r]
            [ragtime.repl :as rr]
            [clojure.string :as str])
  (:import java.nio.ByteBuffer
           java.security.SecureRandom
           java.util.Base64))

(defn to-bytes [n]
  (let [ buf (ByteBuffer/allocate 4)]
    (.putInt buf n)
    (.array buf)))

(defn generate-token [id size]
  (let [bucket (byte-array size)]
    (.nextBytes (SecureRandom.) bucket)
    (.encodeToString (Base64/getUrlEncoder)
                     (byte-array (concat bucket (to-bytes id))))))

(defn id-from-token [token]
  (let [data (.decode (Base64/getUrlDecoder) token)
        [_ bytes] (split-at (- (count data) 4) data)]
    (.getInt (ByteBuffer/wrap (byte-array bytes)))))

(def db-spec
  (delay
   (let [ds {:datasource
             (cp/make-datasource
              {:adapter "h2"
               :url (str "jdbc:h2:" (or (System/getProperty "DB_FILE") "~/teamtv-db"))})}]
     (rr/migrate {:datastore (r/sql-database ds)
                  :migrations (r/load-resources "migrations")} )
     ds)))

(defn find-user [criteria]
  (let [[where params] (reduce
                        (fn [[cols vals] [k v]] [(conj cols (str (name k) "=?")) (conj vals v)])
                        [[] []] (select-keys criteria [:email :id]))]
    (first
     (jdbc/query @db-spec (cons (str "select * from app_users where " (str/join " and " where)) params)))))

(defn create-user [data]
  (first (jdbc/insert! @db-spec "app_users"
                       (-> data (select-keys [:email])
                           (assoc :status "REGISTERED")))))

(defn login-request [login]
  (let [user (or (find-user login) (create-user login))
        token (generate-token (:id user) 40)]
    (jdbc/update! @db-spec "app_users" {:random_token token} ["id = ?" (:id user)])
    token))

(defn verify-token [token]
  (let [id (id-from-token token)]
    (when (= 1 (first (jdbc/update! @db-spec "app_users" {:random_token nil}
                                    ["id = ? and random_token = ?" id token])))
      (find-user {:id id}))))

(defn create-project [proj]
  (first (jdbc/insert! @db-spec "projects"
                       (-> proj (select-keys [:owner :content :name])
                           (update :content pr-str)))))

(defn find-projects [{:keys [name]}]
  (jdbc/query @db-spec ["select id, name from projects where name like ? order by create_at desc"
                        name]))

(defn get-project [proj-id]
  (first (jdbc/query @db-spec ["select * from projects where id=?" proj-id])))

(defn update-project [proj-id proj]
  (first (jdbc/update! @db-spec "projects"
                 (-> proj (select-keys [:name :content])
                     (update :content pr-str))
                 ["owner=? and id=?" (:owner proj) proj-id])))
