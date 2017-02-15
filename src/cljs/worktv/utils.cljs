(ns worktv.utils
  (:require [cljs.core.async :refer [>! chan]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn tablify [data]
  (cond
    (map? data) (let [[_ v] (first data)
                      [headers valfns] (if-let [ks (and (map? v) (keys v))]
                                         [(cons "$key" (map name ks)) (cons first ks)]
                                         [["$key" "$value"] [first second]])]
                  (cons headers (map (juxt valfns) data)))
    (coll? data) (let [x (first data)
                       [headers valfns] (if-let [ks (and (map? v) (keys v))]
                                          [(map name ks) ks]
                                          [["$value"] [identity]])]
                   (cons headers (map (juxt valfns) data)))))

(defn fetch-data [url path]
  (let [ch (chan)]
    (GET url :format json :handler #(go (>! ch [%])) :error-handler #(go (>! ch [nil %])))
    (go (let [[data error] (<! ch)]
          (if data )))))
