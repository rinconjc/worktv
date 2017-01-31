(ns worktv.views
  (:require [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [commons-ui.core :as c]
            [reagent.core :as r :refer-macros [with-let]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def modal (r/atom nil))

(defn modal-dialog [{:keys [title ok-fn]} content]
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
          [:button.btn.btn-primary {:on-click #(go (if-let [err (-> (<! (ok-fn)) :error)]
                                                     (reset! error [c/alert "danger" err])
                                                     (reset! modal nil)))} "OK"])
        [:button.btn {:on-click #(reset! modal nil)} "Close"]]]]]))

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
