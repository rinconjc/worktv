(ns worktv.layout
  (:require [worktv.splitter :refer [splitter]]))

;; (s/def ::pane-type )
;; (s/def ::pane (s/keys :req [::pane-type]))
;; pane = content-pane | container-pane
;; content-pane = content-type src attrs
;; container-pane = top bottom | left right

(defmulti content-view :content-type)

(defmulti pane-view :type)

(defmethod pane-view :content-pane [pane]
  (content-view pane))

(defmethod pane-view :container-pane [{:keys [pane1 pane2] :as opts}]
  [splitter opts (pane-view pane1) (pane-view pane2)])

(defmethod content-view :image [{:keys [url fill? title]}]
  [:div
   (if title [:h4.top-relative title])
   [:img {:src url :class (if fill? "fill" "")}]])

(defmethod content-view :video [{:keys [url fill? title]}]
  [:video {:src url :class (if fill? "fill" "")}])

(defmethod content-view :default [pane]
  [:div.fill "blank content"])

(defn layout-editor [root-pane on-change]
  (pane-view root-pane))
