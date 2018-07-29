(ns worktv.utils
  (:require [ajax.core :refer [default-interceptors GET to-interceptor]]
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

(defn handle-keys [key action & more]
  (let [keys (str/split key #"\s*\+\s*")]
    (fn [e]
      (or
       (when (every? #(case %
                        "ctrl" (.-ctrlKey e)
                        "alt" (.-altKey e)
                        "shift" (.-shiftKey e)
                        (= (str/upper-case %) (String/fromCharCode (.-keyCode e)))) keys)
         (.preventDefault e)
         (action e)
         true)
       (if-not (empty? more)
         (-> handle-keys (apply more) (apply [e])))))))

(defn event-no-default [f]
  (fn [e]
    (.preventDefault e)
    (f)))

;; ============ hack to handle success empty responses
(defn empty-means-nil [response]
  (if (empty? (ajax.protocols/-body response))
    (reduced [ajax.protocols/-status nil])
    response))

(def treat-nil-as-empty
  (to-interceptor {:name "JSON special case nil"
                   :response empty-means-nil}))

(reset! default-interceptors [treat-nil-as-empty])

;; ============= promise to channel ============
(defn to-chan [p]
  (let [ch (chan)]
    (-> p
        (.then #(go (>! ch {:ok %})))
        (.catch #(go (>! ch {:error %}))))
    ch))

(defn map-chan [ch f]
  (go (let [{:keys [success] :as r} (<! ch)]
        (if success {:ok (f success)} r))))

(defn flat-map-chan [ch f]
  (go (let [{:keys [success] :as r} (<! ch)]
        (if success (<! (f success)) r))))

;; ========================

(defn async-http
  "Executes xhr call in background and returns a channel with the
  results {:ok response} or {:error error}"
  [method uri opts]
  (let [ch (chan)]
    (method uri
            (assoc opts
                   :handler #(go
                               (js/console.log "success " uri)
                               (>! ch {:ok %}))
                   ;; :format :json
                   ;; :response-format :json
                   :error-handler #(go
                                     (js/console.log "error" uri %)
                                     (>! ch {:error (:response %)}))
                   :keywords? true))
    ch))
