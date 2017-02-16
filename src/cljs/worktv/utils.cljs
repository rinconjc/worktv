(ns worktv.utils
  (:require [ajax.core :refer [GET]]
            [cljs.core.async :refer [>! chan]]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn visit [x f] (f x) x)

(defn tablify [data]
  (cond
    (map? data) (let [[_ v] (first data)
                      [headers valfns] (if-let [ks (and (map? v) (keys v))]
                                         [(cons "$key" (map name ks)) (cons first ks)]
                                         [["$key" "$value"] [first second]])]
                  (cons headers (map (apply juxt valfns) data)))
    (coll? data) (let [v (first data)
                       [headers valfns] (if-let [ks (and (map? v) (keys v))]
                                          [(map name ks) ks]
                                          [["$value"] [identity]])]
                   (cons headers (map (apply juxt valfns) data)))))

(defn fetch-data [url path]
  (let [ch (chan)]
    (GET url :format :json :response-format :json :handler #(go (>! ch [%]))
         :keywordize-keys true :error-handler #(go (>! ch [nil %])))
    (go (let [[data error] (<! ch)]
          (if data
            [(tablify (get-in data (str/split path #"\.") ((partial map keyword))))]
            [nil error])))))
