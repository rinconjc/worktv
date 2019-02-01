(ns worktv.views
  (:require [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [commons-ui.core :as c]
            [reagent.core :refer [atom] :as r :refer-macros [with-let]]
            [worktv.utils :as u]
            [worktv.backend :as b]
            [re-frame.core :refer [subscribe dispatch]]
            [ajax.core :refer [GET]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(doto (-> js/google .-charts)
  (.load "current" #js {:packages #js ["corechart"]})
  (.setOnLoadCallback #(js/console.log "g chart loaded!!....")))

(def modal (r/atom nil))

(defn table-view [[headers & rows]]
  [:table.table.table-bordered
   [:thead
    [:tr
     (doall (for [h headers] ^{:key h}[:th h]))]]
   [:tbody
    (doall
     (map-indexed
      (fn [i r] ^{:key i}[:tr (doall (for [c r] ^{:key c}[:td c]))])
      rows))]])

(defn generate-preview [url path]
  (let [content (atom nil)]
    (when (and url path)
      (go (let [[data error] (<! (u/fetch-data url path))]
            (if data
              (reset! content [:div [:label "Data Preview"] [table-view (take 5 data)]])
              (reset! content [c/alert {:type "danger"} (or error "unknown error")])))))
    content))

(defn modal-dialog []
  (with-let [modal (subscribe [:modal])]
    (when-let [{:keys [title ok-fn content ok-event] :as modal} @modal]
      [:div.modal {:style {:display "block"} :tabIndex -1}
       [:div.modal-dialog
        [:form.modal-content
         (when (or (fn? ok-fn) ok-event)
           {:on-submit (u/event-no-default
                        (fn [_]
                          (if (fn? ok-fn) (ok-fn) (dispatch ok-event))
                          (dispatch [:close-modal])))})
         [:div.modal-header [:h4 title]]
         [:div.modal-body
          (when (:error modal)
            [c/alert (:error modal)])
          content
          [:div.modal-footer
           (when (or (fn? ok-fn) ok-event)
             [:button.btn.btn-primary "OK"])
           [:button.btn {:type "button" :on-click #(dispatch [:close-modal])} "Close"]] ]]]])))

(defn save-form [data]
  [:div
   [c/input {:type "text" :id "name" :label "Project Name:" :placeholder "Name of your projects"
             :model [data :name] :validator #(not (str/blank? %))}]
   [c/input {:type "radio" :name "folder" :label "Sharing:" :text "Public" :model [data :folder]
             :items {"public" "Public" "private" "Private"}}]
   [c/input {:type "textarea" :id "description" :label "Description:" :rows 3
             :model [data :description]}]])

(defn search-project-form []
  (with-let [proj-search (subscribe [:project-search])]
    [:div.list-group
     (doall
      (for [{:keys [id name description]} (:projects @proj-search)]
        ^{:key id}[:a.list-group-item {:href "#" :on-click #(dispatch [:project-search-select id])}
                   [:h4.list-group-item-heading name]
                   [:p.list-group-item-text description]]))]))

(defn y-serie-form [add-fn]
  (with-let [form (atom {})]
    [:tr
     [:td [c/bare-input {:type "text" :model [form :column]}]]
     [:td [c/bare-input {:type "text" :model [form :label]}]]
     [:td [:button.btn
           {:on-click #(do (add-fn (:column @form) (:label @form)) (reset! form {}))}
           [:i.fa.fa-plus]]]]))

(defn chart-form [form]
  [:div
   [c/input {:type "text" :label "Title" :model [form :title]}]
   [c/input {:type "text" :label "Data source URL" :model [form :url]}]
   [:div.form.form-inline
    [c/input {:type "text" :label "Data Path: " :model [form :data-path]}]
    [c/input {:type "select" :label "Chart Type: " :model [form :chart-type]
              :options [[:line "Line Chart"]
                        [:bar "Bar Chart"]
                        [:pie "Pie Chart"]]}]]
   [:div.form-group @@(r/track generate-preview (:url @form) (:data-path @form))]
   [:div.form.form-inline
    [c/input {:type "text" :label "X Value :" :model [form :x-path]}]
    [c/input {:type "text" :label "X Label :" :model [form :x-label]}]]
   [:div.form-group
    [:label "Y series"]
    [:table.table.table-striped
     [:thead [:tr [:th "Column"] [:th "Label"] [:th]]]
     [:tbody (doall (for [[column label] (:y-series @form)]
                      ^{:key column}[:tr [:td column] [:td label]
                                     [:td [:button.btn
                                           {:on-click #(swap! form update :y-series dissoc column)}
                                           [:i.fa.fa-minus]]]]))
      [y-serie-form #(swap! form update :y-series assoc %1 %2)]]]]])

(defn web-page-form [form]
  [:div
   [c/input {:type "text" :label "URL" :placeholder "Past page URL" :model [form :url]}]
   (if-let [url (:url @form)]
     [:div.full.fill
      [:embed.full.fill {:src url}]])])

(defn image-list [ch]
  (with-let [urls (atom nil)]
    (go (if-let [r (<! ch)]
          (reset! urls r)))
    (if @urls
      (cond
        (coll? @urls)
        [:div {:style {:max-height "400px" :overflow-y "scroll"}}
         (doall
          (map-indexed
           (fn [i group] ^{:key i}
             [:div (doall (for [{:keys [url image]} group] ^{:key url}
                            [:a {:href "#" :on-click #(.preventDefault %)}
                             [:img {:src image :data-url url
                                    :style {:max-width "260px" :max-height "260px"}}]]))])
           (partition 2 @urls)) )]
        :else [:img {:src @urls :style {:max-width "400px" :max-height "400px"}}]))))

(defn image-form [form]
  (with-let [search-fn (u/throtled b/search-images 500)]
    [:div
     [:form.form.form-horizontal
      [c/input {:type "text" :label "Title" :model [form :title]
                :placeholder "Optional title" :wrapper-class "col-sm-10" :label-class "col-sm-2"}]
      [c/input {:type "text" :label "URL" :model [form :url] :placeholder "Image URL or search text"
                :wrapper-class "col-sm-10" :label-class "col-sm-2"}]
      [c/input {:type "radio" :label "Display" :model [form :display] :name "display"
                :wrapper-class "col-sm-10" :label-class "col-sm-2"
                :items {"fit-full" "Fill" "clipped" "Clip"}}]]
     [:div {:on-double-click #(if-let [url (-> % .-target (.getAttribute "data-url"))]
                                (swap! form assoc :url url))}
      [image-list @(r/track search-fn (:url @form))]]]))

(defn apply-template [template data]
  (js/console.log "formatting..." )
  ((js/Handlebars.compile template) (clj->js data)))

(defn fetch-data [url result]
  (when-not (str/blank? url)
    (GET url {:handler #(reset! result %)
              :error-handler #(js/console.log "Error " %)})))

(defn custom-form [form]
  (with-let [preview-on (atom false)
             data (atom nil)
             output (atom nil)]
    [:div
     [c/input {:type "text" :label "Title" :model [form :title] :placeholder "Optional title"}]
     [c/input {:type "text" :label "Data URL" :model [form :url] :placeholder "URL of data"
               :on-blur #(fetch-data (-> % .-target .-value) data)}]
     [c/input {:type "text" :label "Refresh Interval (secs)" :model [form :refresh-interval]
               :placeholder "60"}]
     [:ul.nav.nav-tabs
      [:li {:class (when-not @preview-on "active") :on-click #(reset! preview-on false)}
       [:a {:href "#"} "Template"]]
      [:li {:class (when @preview-on "active") :on-click #(do
                                                            (reset! output (apply-template (:template @form) @data))
                                                            (reset! preview-on true))}
       [:a {:href "#"} "Preview"]]]
     [:div.tab-content
      [:div.tab-pane {:class (when-not @preview-on  "active")}
       [c/input {:type "textarea" :label "HTML template" :model [form :template] :placeholder "mustache template" :rows 10}]]
      [:div.tab-pane {:class (when @preview-on "active")}
       [:div {:dangerouslySetInnerHTML {:__html @output}}]]]]))

(defn with-node [data node-fn]
  (r/create-class
   {:reagent-render (fn [data node-fn] [:div.fill.full {:ref "chart" :width "100%" :height "100%"}])
    :component-did-mount (fn [this] (node-fn (-> this .-refs .-chart) data))
    :component-will-update (fn [this [_ data node-fn]] (node-fn (-> this .-refs .-chart) data))
    :should-component-update (fn [this] true)}))

(defn chart-view [pane]
  [with-node pane
   (fn [elem {:keys [title url data-path x-path x-label y-series] :as pane}]
     (if (and url data-path)
       (go
         (let [gviz (.-visualization js/google)
               columns (cons x-path (keys y-series))
               labels (cons x-label (vals y-series))
               [data error] (<! (u/fetch-data url data-path columns))
               data (if data (->> data rest (sort-by #(nth % 0)) (cons labels) clj->js))]
           (js/console.log "rendering chart..." title)
           (if data
             (.. (js/google.visualization.LineChart. elem)
                 (draw (.arrayToDataTable gviz data) #js {:title title :curveType "function"
                                                          :legend #js {:position "bottom"}}))
             (js/console.log "error:" (clj->js error)))))))])

(defn slides-form [form]
  [:div.row
   [:div.col-md-2.col-sm-5
    [c/input {:type "number" :label "Transition Interval (secs)" :model [form :interval]}]]])

(defn rich-editor [attrs]
  (r/create-class
   {:reagent-render (fn [attrs] [:div {:dangerouslySetInnerHTML {:__html (:content attrs)}} ])
    :component-did-mount
    (fn [c]
      (let [editor (js/Quill. (r/dom-node c) #js
                              {:debug "warn" :theme "snow"
                               :modules #js {:toolbar (clj->js
                                                       [[{:font []}]
                                                        [{:header [false 1 2 3 4 5]}]
                                                        ["bold" "italic" "underline"]
                                                        ["link" "image"]
                                                        [{:color []} {:background []}]
                                                        [{:align []}]])}})]
        (.pasteHTML editor (:content attrs))
        (when-let [f (:on-change attrs)]
          (.on editor "text-change" #(f (-> editor .-root .-innerHTML))))))}))

(defn html-form [form]
  [rich-editor (assoc @form :on-change #(swap! form assoc :content % ))])

(defn publish-form [data]
  [:div
   [c/input {:type "text" :label "Name" :model [data :name]
             :placeholder "unique name of your publishing" :required true}]
   [:div (str (.-origin js/location) "/view/" (:name @data))]])
