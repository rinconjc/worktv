(defproject worktv "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [ring-server "0.5.0"]
                 [reagent "0.8.1"]
                 [reagent-utils "0.3.1"]
                 [ring "1.6.3"]
                 [ring/ring-defaults "0.3.2"]
                 [compojure "1.6.1"]
                 [hiccup "1.0.5"]
                 [yogthos/config "1.1"]
                 [org.clojure/clojurescript "1.10.238"
                  :scope "provided"]
                 [secretary "1.2.3"]
                 [venantius/accountant "0.2.4"
                  :exclusions [org.clojure/tools.reader]]
                 [cljs-ajax "0.7.3"]
                 [cljsjs/mustache "2.2.1-0"]
                 [commons-ui "0.1.0-SNAPSHOT"]
                 [org.clojure/core.async "0.4.474"
                  :Exclusions [org.clojure/tools.reader]]
                 [ring/ring-json "0.4.0"]
                 [clj-http "3.9.0"]
                 [com.h2database/h2 "1.4.197"]
                 [cljsjs/firebase "3.5.3-0"]
                 [com.draines/postal "2.0.2"]
                 [org.clojure/core.match "0.3.0-alpha5"]
                 [ragtime "0.7.2"]
                 [hikari-cp "2.5.0"]
                 [org.clojure/core.match "0.3.0-alpha5"]
                 [buddy "2.0.0"]
                 [buddy/buddy-sign "2.0.0"]
                 [org.slf4j/slf4j-simple "1.7.25"]
                 [re-frame "0.10.5"]
                 [prismatic/plumbing "0.5.5"]
                 [ring-middleware-format "0.7.2"]]

  :plugins [[lein-environ "1.0.2"]
            [lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]
            [lein-asset-minifier "0.2.7"
             :exclusions [org.clojure/clojure]]
            [refactor-nrepl "2.3.1"]
            [cider/cider-nrepl "0.14.0"]]

  :ring {:handler worktv.handler/app
         :uberwar-name "worktv.war"}

  :min-lein-version "2.5.0"

  :uberjar-name "worktv.jar"

  :main worktv.server

  :clean-targets ^{:protect false}
  [:target-path
   [:cljsbuild :builds :app :compiler :output-dir]
   [:cljsbuild :builds :app :compiler :output-to]]

  :source-paths ["src/clj" "src/cljc" "src/cljs"]
  :resource-paths ["resources" "target/cljsbuild"]

  :minify-assets
  {:assets
   {"resources/public/css/site.min.css" "resources/public/css/site.css"
    "resources/public/css/splitter.min.css" "resources/public/css/splitter.css"}}

  :cljsbuild
  {:builds {:min
            {:source-paths ["src/cljs" "src/cljc" "env/prod/cljs"]
             :compiler
             {:output-to "target/cljsbuild/public/js/app.js"
              :output-dir "target/uberjar"
              :optimizations :advanced
              :pretty-print  false
              :externs ["externs/externs.js"]}}
            :app
            {:source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
             :compiler
             {:main "worktv.dev"
              :asset-path "/js/out"
              :output-to "target/cljsbuild/public/js/app.js"
              :output-dir "target/cljsbuild/public/js/out"
              :source-map true
              :optimizations :none
              :pretty-print  true}}}}


  :figwheel
  {:http-server-root "public"
   :server-port 3447
   :nrepl-port 7001
   :nrepl-middleware ["cemerick.piggieback/wrap-cljs-repl"
                      ]
   :css-dirs ["resources/public/css"]
   :ring-handler worktv.handler/app}



  :profiles {:dev {:repl-options {:init-ns worktv.repl
                                  :nrepl-middleware [cider.piggieback/wrap-cljs-repl]}

                   :dependencies [[ring/ring-mock "0.3.0"]
                                  [ring/ring-devel "1.5.0"]
                                  [prone "1.1.2"]
                                  [figwheel-sidecar "0.5.16"]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [cider/piggieback "0.3.6"]
                                  [pjstadig/humane-test-output "0.8.1"]
                                  ]

                   :source-paths ["env/dev/clj"]
                   :plugins [[lein-figwheel "0.5.16"]
                             ]

                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]

                   :env {:dev true}}

             :uberjar {:hooks [minify-assets.plugin/hooks]
                       :source-paths ["env/prod/clj"]
                       :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                       :env {:production true}
                       :aot :all
                       :omit-source true}})
