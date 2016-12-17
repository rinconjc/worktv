(ns worktv.layout
  (:require [reagent.core :refer [atom]]
            [worktv.splitter :refer [splitter]]))

;; (s/def ::pane-type )
;; (s/def ::pane (s/keys :req [::pane-type]))
;; pane = content-pane | container-pane
;; content-pane = content-type src attrs
;; container-pane = top bottom | left right

(def selected-pane (atom nil))
(def alert (atom nil))

(defn is-selected [pane]
  (identical? pane (first @selected-pane)))

(defmulti content-view :content-type)

(defmulti pane-view :type)

(defmethod pane-view :content-pane [pane on-change]
  [:div.fill.full
   {:on-click #(do
                 (reset! selected-pane (if (is-selected pane) nil [pane on-change]))
                 false)
    :on-dragenter #(-> .-target .-classList (.add "drag-over"))
    :on-dragleave #(-> .-target .-classList (.remove "drag-over"))
    :on-dragdrop #(-> .-dataTransfer (.getData "text"))
    :class (if (is-selected pane)  "selected-pane")}
   (content-view pane on-change)])

(defmethod pane-view :container-pane [{:keys [pane1 pane2] :as opts} on-change]
  [splitter opts
   (pane-view pane1 #(on-change (if (nil? %) pane2 (assoc opts :pane1 %))))
   (pane-view pane2 #(on-change (if (nil? %) pane1 (assoc opts :pane2 %)))) on-change])

(defmethod content-view :image [{:keys [url fill? title]} on-change]
  [:div
   (if title [:h4.top-relative title])
   [:img {:src url :class (if fill? "fill" "")}]])

(defmethod content-view :video [{:keys [url fill? title]} on-change]
  [:video {:src url :class (if fill? "fill" "")}])

(defmethod content-view :default [pane on-change]
  [:div.fill "blank content"])

(defn layout-editor [root-pane on-change]
  [:div.fill.full
   (if @alert [:div.alert.alert-fixed.alert-dismissible {:class (str "alert-" (first @alert))}
               [:button.close {:data-dismiss "alert" :aria-label "Close"}
                [:span {:aria-hidden true} "×"]] (second @alert)])
   (pane-view root-pane on-change)])

(defn split-pane [orientation]
  (if-not @selected-pane (reset! alert ["danger" "Please select the pane to split first"])
          (let [[pane on-change] @selected-pane]
            (on-change (assoc pane
                              :type :container-pane :orientation orientation
                              :pane1 {:type :content-pane}
                              :pane2 {:type :content-pane}))
            (reset! selected-pane nil))))

(defn delete-pane []
  (if-not @selected-pane (reset! alert ["danger" "Please select the pane to split first"])
          ((second @selected-pane) (reset! selected-pane nil))))
