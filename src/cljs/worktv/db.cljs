(ns worktv.db)

(def content-types [{:type :image :label "Image" :icon "fa-image"}
                    {:type :video :label "Video" :icon "fa-file-video"}
                    {:type :custom :label "Custom" :icon "fa-code"}
                    {:type :chart :label "Chart" :icon "fa-chart-bar"}
                    {:type :page :label "Web Page" :icon "fa-newspaper"}
                    {:type :slides :label "Slides" :icon "fa-film" }])

(def blank-design {:layout {1 {:id 1 :type :content-pane}} :screen "1280x720"})
