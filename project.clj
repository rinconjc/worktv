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
                 [org.clojure/clojurescript "1.10.339"]
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
                 [ring-middleware-format "0.7.2"]
                 [sablono "0.8.4"]
                 [cljsjs/codemirror "5.40.2-1"]]

  :plugins [[lein-environ "1.1.0"]
            [lein-cljsbuild "1.1.7"]
            [lein-asset-minifier "0.2.7"
             :exclusions [org.clojure/clojure]]
            [lein-cljsasset "0.2.0"]]

  :ring {:handler worktv.handler/app
         :uberwar-name "worktv.war"}

  :min-lein-version "2.7.1"

  :uberjar-name "worktv.jar"

  :main worktv.server

  :jvm-opts ["-Dconfig=.local.conf.edn"]

  :clean-targets ^{:protect false}
  [:target-path
   [:cljsbuild :builds :app :compiler :output-dir]
   [:cljsbuild :builds :app :compiler :output-to]]

  :source-paths ["src/clj" "src/cljc" "src/cljs"]
  :resource-paths ["resources" "target/cljsbuild"]

  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dev"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" test.test-runner]}

  :minify-assets
  {:assets
   {"resources/public/css/site.min.css" "resources/public/css/site.css"
    "resources/public/css/splitter.min.css" "resources/public/css/splitter.css"}}

  :cljsasset {:css ["cljsjs/codemirror/production/codemirror.min.css"
                    "cljsjs/codemirror/common/theme/solarized.css"]
              :js  ["cljsjs/codemirror/common/mode/htmlmixed.inc.js"
                    "cljsjs/codemirror/common/addon/hint/html-hint.inc.js"]}
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
             :figwheel true
             :compiler {
                        :main "worktv.dev"
                        :asset-path "/js/out"
                        :output-to "target/cljsbuild/public/js/app.js"
                        :output-dir "target/cljsbuild/public/js/out"
                        :source-map true
                        :optimizations :none
                        :pretty-print  true}}}}

  :profiles {:dev {:repl-options {:init-ns worktv.repl
                                  :nrepl-middleware [cider.piggieback/wrap-cljs-repl]
                                  }
                   :dependencies [[cider/piggieback "0.3.10"]
                                  [prone "1.6.1"]
                                  [com.bhauman/figwheel-main "0.1.9"]
                                  [com.bhauman/rebel-readline-cljs "0.1.4"]
                                  ]

                   :source-paths ["env/dev/clj"]
                   :env {:dev true}}

             :uberjar {:hooks [minify-assets.plugin/hooks]
                       :source-paths ["env/prod/clj"]
                       :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                       :env {:production true}
                       :aot :all
                       :omit-source true}})
