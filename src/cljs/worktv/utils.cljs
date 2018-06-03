(ns worktv.utils
  (:require [ajax.core :refer [GET]]
            [cljs.core.async :refer [>! chan offer! sliding-buffer timeout]]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn visit [x f] (f x) x)

(def  date-operand-pattern #"\s*(\+|-)\s*(\d+)\s*(\w+)")
(def  date-exp-pattern #"(\w+)((\s*(\+|-)\s*\d+\s*\w+)*)(:(.+))?")
(def  date-fields (as-> {"sec" 1000} fields
                    (assoc fields "min" (* 60 (fields "sec")))
                    (assoc fields "hour" (* 60 (fields "min")))
                    (assoc fields "day" (* 24 (fields "hour")))
                    (assoc fields "week" (* 7 (fields "day")))))

(defn- eval-date-exp [cal [num name]]
  (case name
    "month" (let [month (+ (.getMonth cal) num)]
              (if (neg? month)
                (doto cal (.setFullYear (+ (.getFullYear cal) (Math/floor (/ month 12))))
                      (.setMonth (+ 11 (rem month 12))))
                (doto cal (.setFullYear (+ (.getFullYear cal) (Math/floor (/ month 12))))
                      (.setMonth (rem month 12)))))
    (-> name date-fields (* num) (+ (.getTime cal)) (js/Date.))))

(defn date-from-name [name]
  (let [cal (js/Date.)]
    (case name
      "now" cal
      "today" cal
      "tomorrow" (eval-date-exp cal [1 "day"])
      "yesterday" (eval-date-exp cal [-1 "day"])
      nil)))

(defn- extract [date key]
  (case key
    "yy" (-> date .getFullYear (.subtr 2 2))
    "yyyy" (.getFullYear date)
    "d" (.getDate date)
    "M" (inc (.getMonth date))
    "H" (.getHours date)
    "m" (.getMinutes date)
    "s" (.getSeconds date)
    "S" (.getMilliseconds date)
    "SSS" (as-> (.getMilliseconds date) v (str (condp > v 10 "00" 100 "0") v))
    (as-> (case key
            "MM" (-> date .getMonth inc)
            "dd" (.getDate date)
            "HH" (.getHours date)
            "mm" (.getMinutes date)
            "ss" (.getSeconds date)
            key) v (if (and (int? v) (< v 10)) (str "0" v) (str v)))))

(defn format-date [date fmt]
  (let [parts (reduce (fn [ss c]
                        (if (and (not-empty ss) (= c (-> ss last last)))
                          (update ss (dec (count ss)) str c)
                          (conj ss (str c)))) [] fmt)]
    (reduce #(str %1 (extract date %2)) "" parts)))

(defn parse-date-exp [s]
  (if-let [[_ name operands _ _ _ fmt] (re-matches date-exp-pattern s)]
    (if-let [cal (date-from-name name)]
      (let [operations (some->> operands (re-seq date-operand-pattern)
                                (map (fn [[_ op n name]]
                                       [(* (if (= op "+") 1 -1) (js/parseInt n)) name])))
            cal (if operations (reduce eval-date-exp cal operations) cal)]
        (format-date cal (or fmt "yyyy-MM-dd"))))))

(defn expand-url [url]
  (str/replace url #"\$([^\$]+)\$"
               #(parse-date-exp (second %))))

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
   (let [ch (chan)
         url (expand-url url)]
     (GET url :format :json :response-format :json :handler #(go (>! ch [%]))
          :keywordize-keys true :error-handler #(go (>! ch [nil %])))
     (go (let [[data error] (<! ch)]
           (if data
             [(tablify (get-in data (str/split path #"\.") ((partial map keyword))) columns)]
             [nil error]))))))

(defn throtled
  "returns a wrapper function that invokes the function f at least msecs apart!"
  [f msecs]
  (let [c (chan (sliding-buffer 1))
        timer (chan)
        r (chan)]
    (go
      (<! timer)
      (loop [last-arg nil]
        (let [[val ch] (alts! [c (timeout msecs)])]
          (if (identical? c ch) (recur val)
              (do (some->> (<! (apply f last-arg)) (>! r))
                  (recur nil)))))); apply f to the latest args
    (fn [& args]
      (go (>! c args)
          (offer! timer :start))
      r)))

(defn- match-key [key e]
  (case key
    "ctrl" (.-ctrlKey e)
    "alt" (.-altKey e)
    "shift" (.-shiftKey e)
    (= key (String/fromCharCode (.-keyCode e)))))

(defn handle-key [key action]
  (let [keys (str/split key "\s*\+\s*")]
    (fn [e]
      (.preventDefault e)
      (every? match-key keys))))
