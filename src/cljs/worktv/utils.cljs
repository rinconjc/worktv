(ns worktv.utils
  (:require [ajax.core :refer [GET]]
            [cljs.core.async :refer [>! chan sliding-buffer timeout]]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn visit [x f] (f x) x)

(defn- headers-and-extractors [data]
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
  (->> xs (keep-indexed #(if (= %2 x) %1 nil)) first))

(defn tablify
  "transforms a data structure into a table (sequence of sequences)"
  ([data] (tablify data nil))
  ([data columns]
   (let [[headers valfns] (headers-and-extractors data)
         [headers valfns] (if (seq? columns)
                            [columns (map #(nth valfns (index-of headers %)) columns)]
                            [headers valfns])]
     (cons headers (map (apply juxt valfns) data)))))

(defn fetch-data
  "retrieves tablified data from the given url and json-path"
  ([url path] (fetch-data url path nil))
  ([url path columns]
   (let [ch (chan)]
     (GET url :format :json :response-format :json :handler #(go (>! ch [%]))
          :keywordize-keys true :error-handler #(go (>! ch [nil %])))
     (go (let [[data error] (<! ch)]
           (if data
             [(tablify (get-in data (str/split path #"\.") ((partial map keyword))) columns)]
             [nil error]))))))

(defn throtled
  "returns a wrapper function that invokes the function f at least msecs apart!"
  [f msecs]
  (let [c (sliding-buffer 1)
        timer (chan)
        r (chan)]
    (go (<! timer) ; wait for a start!
        (loop []
          (<! (timeout msecs)) ; wait for timeout!
          (>! r (apply f (<! c)))
          (recur []))); apply f to the latest args
    (fn [& args]
      (go (>! c args)
          (offer! timer :start))
      r)))
