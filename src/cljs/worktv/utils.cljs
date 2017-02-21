(ns worktv.utils
  (:require [ajax.core :refer [GET]]
            [cljs.core.async :refer [>! chan]]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn visit [x f] (f x) x)

(defn- headers-and-extractors [data]
  (js/console.log "data:" (clj->js data))
  (cond
    (map? data) (let [[_ v] (first data)]
                  (if-let [ks (and (map? v) (keys v))]
                    [(cons "$key" (map name ks)) (cons first ks)]
                    [["$key" "$value"] [first second]]))
    (coll? data) (let [v (first data)]
                   (if-let [ks (and (map? v) (keys v))]
                     [(map name ks) ks]
                     [["$value"] [identity]]))))

(defn index-of [xs x]
  (js/console.log "searching..." x " in " (clj->js xs))
  (->> xs (keep-indexed #(if (= %2 x) %1 nil)) first))

(defn tablify
  "transforms a data structure into a table (sequence of sequences)"
  ([data] (tablify data nil))
  ([data columns]
   (let [[headers valfns] (headers-and-extractors data)
         [headers valfns] (if (seq? columns)
                            [columns (map #(nth (index-of headers %) valfns) columns)]
                            [headers valfns])]
     (cons headers (map (apply juxt valfns) data)))))

(defn fetch-data [url path]
  (let [ch (chan)]
    (GET url :format :json :response-format :json :handler #(go (>! ch [%]))
         :keywordize-keys true :error-handler #(go (>! ch [nil %])))
    (go (let [[data error] (<! ch)]
          (if data
            [(tablify (get-in data (str/split path #"\.") ((partial map keyword))))]
            [nil error])))))
