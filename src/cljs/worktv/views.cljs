(ns worktv.views
  (:require [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [commons-ui.core :as c]
            [reagent.core :as r :refer-macros [with-let]]
            [cljsjs.d3]
            [worktv.utils :as u])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(doto (-> js/google .-charts)
  (.load "current" #js {:packages #js ["corechart"]})
  (.setOnLoadCallback #(js/console.log "g chart loaded!!....")))


(def modal (r/atom nil))

(defn table-view [[headers & rows]]
  [:table
   [:tr
    (doall (for [h headers] ^{:key h}[:th h]))]
   (doall (map-indexed
           (fn [i r] ^{:key i}[:tr (doall (for [c r] ^{:key c}[:td c]))])
           rows))])

(defn data-preview [form]
  (with-let [content (atom nil)]
    (if-let [[url path] (-> @form ((juxt :url :data-path))
                            ((partial filter #(and (first %) (second %)))))]
      (go (let [[data error] (u/visit (<! (u/fetch-data url path)) (comp clj->js js/console.log) )]
            (js/console.log "fetched:" (clj->js data) error)
            (if data
             (reset! content [table-view (take 10 data)])
             (reset! content [c/alert {:type "danger"} error])))))
    [:div.row @content (js/console.log "rendering elem...")]))

(defn modal-dialog [{:keys [title ok-fn close-fn]} content]
  (with-let [error (atom nil)]
    [:div.modal {:style {:display "block"} :tabIndex -1}
     [:div.modal-dialog
      [:div.modal-content
       [:div.modal-header [:h4 title]]
       [:div.modal-body
        @error
        content]
       [:div.modal-footer
        (if (fn? ok-fn)
          [:button.btn.btn-primary
           {:on-click #(go (if-let [err (-> (<! (ok-fn)) :error)]
                             (reset! error (c/alert {:type :danger} err))
                             (reset! modal nil)))} "OK"])
        [:button.btn {:on-click #(do (reset! modal nil)
                                     (if (fn? close-fn) (close-fn)) )} "Close"]]]]]))

(defn save-form [data]
  [:form.form
   [c/input {:type "text" :id "name" :label "Project Name:" :placeholder "Name of your projects"
             :model [data :name] :validator #(not (str/blank? %))}]
   [c/input {:type "radio" :name "folder" :label "Sharing:" :text "Public" :model [data :folder]
             :items {"public" "Public" "private" "Private"}}]
   [c/input {:type "textarea" :id "description" :label "Description:" :rows 3
             :model [data :description]}]])

(defn search-project-form [projs selection]
  [:form.form
   [:div.list-group
    (doall
     (for [[k v] (take 10 projs)]
       ^{:key k}[:a.list-group-item {:href "#" :on-click #(reset! selection [k v])}
                 [:h4.list-group-item-heading (.-name v)]
                 [:p.list-group-item-text (.-description v)]]))]])

(defn chart-form [form]
  [:form.form
   [c/input {:type "text" :label "Title" :model [form :title]}]
   [c/input {:type "text" :label "Data source URL" :model [form :url]}]
   [c/input {:type "text" :label "Data Path" :model [form :data-path]}]
   [data-preview form]
   [c/input {:type "select" :label "Chart Type" :model [form :chart-type]
             :options [[:line "Line Chart"]
                       [:bar "Bar Chart"]
                       [:pie "Pie Chart"]]}]
   [c/input {:type "text" :label "X Label" :model [form :x-label]}]
   [c/input {:type "text" :label "X Values" :model [form :x-path]}]
   [c/input {:type "text" :label "Y Label" :model [form :y-label]}]
   [c/input {:type "text" :label "Y Values" :model [form :y-path]}]] )

(defn image-form [form]
  [:form.form
   [c/input {:type "text" :label "Title" :model [form :title] :placeholder "Optional title"}]
   [c/input {:type "text" :label "URL" :model [form :url] :placeholder "Image URL"}]
   [c/input {:type "radio" :label "Display" :model [form :display]
             :items {"fit-full" "Fill" "clipped" "Clip"}}]])
(defn custom-form [form]
  [:form.form
   [c/input {:type "text" :label "Title" :model [form :title] :placeholder "Optional title"}]
   [c/input {:type "text" :label "Data URL" :model [form :url] :placeholder "URL of data"}]
   [c/input {:type "text" :label "Refresh Interval (secs)" :model [form :refresh-interval]
             :placeholder "60"}]
   [c/input {:type "textarea" :label "HTML template" :model [form :template] :placeholder "mustache template" :rows 10}]])

(defn with-node [node-fn]
  (r/create-class
   {:reagent-render (fn [] [:div.fill.full {:ref "chart" :width "100%" :height "100%"}])
    :component-did-mount (fn [this] (node-fn (-> this .-refs .-chart)))}))

(defn chart-view [pane]
  [with-node
   (fn [elem]
     (let [gviz js/google.visualization
           data (.arrayToDataTable gviz
                                   (clj->js [["Year" "Sales" "Label"]
                                             ["2000" 1000 3]
                                             ["2001" 2000 3]
                                             ["2002" 3000 3]
                                             ["2003" 3200 3]
                                             ["2004" 3100 3]]))
           opts #js {:title "Sales" :curveType "function" :legend #js {:position "bottom"}}]
       (.. (gviz.LineChart. elem) (draw data opts))))])
