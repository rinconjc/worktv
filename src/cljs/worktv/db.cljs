(ns worktv.db)

(def content-types (array-map :image {:label "Image" :icon "fa-image"}
                              :video {:label "Video" :icon "fa-file-video"}
                              :custom {:label "Data Feed" :icon "fa-code"}
                              :chart {:label "Chart" :icon "fa-chart-bar"}
                              :page {:label "Document" :icon "fa-newspaper"}
                              :html {:label "Custom Content" :icon "fa-newspaper"}
                              :slides {:label "Slides" :icon "fa-film"
                                       :default {:slides []}}))

(defn default-content [type]
  (-> content-types type :default (assoc :content-type type)))

(def slide-content-types (filter #(#{:image :video :html :chart :custom} (first %)) content-types))

(def blank-design {:layout {1 {:id 1 :type :content-pane}} :screen "1280x720"})
