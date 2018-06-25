(ns worktv.db
  (:require [clojure.java.jdbc :as jdbc]
            [hikari-cp.core :as cp]
            [ragtime.jdbc :as r]
            [ragtime.repl :as rr]))

(def db-spec
  (delay
   (let [ds {:datasource
             (cp/make-datasource
              {:adapter "h2"
               :url (str "jdbc:h2:" (or (System/getProperty "DB_FILE") "~/teamtv-db"))})}]
     (rr/migrate {:datastore (r/sql-database ds)
               :migrations (r/load-resources "migrations")} )
     ds)))


(defn save-login-request [login]
  (jdbc/insert! @db-spec "app_users" (-> login (select-keys [:email]) (assoc :status :REGISTERED))))
