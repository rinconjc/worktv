(ns worktv.server
  (:gen-class)
  (:require [config.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.reload :refer [wrap-reload]]
            [worktv.handler :refer [app]]))

 (defn -main [& args]
   (let [port (Integer/parseInt (or (env :port) "3003"))]
     (run-jetty (wrap-reload app) {:port port :join? false})))
