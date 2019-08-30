(defproject worktv "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [ring-server "0.5.0"]
                 [reagent "0.8.1"]
                 [reagent-utils "0.3.3"]
                 [ring "1.7.1"]
                 [ring/ring-defaults "0.3.2"]
                 [compojure "1.6.1"]
                 [hiccup "1.0.5"]
                 [yogthos/config "1.1"]
                 [org.clojure/clojurescript "1.10.520"]
                 [secretary "1.2.3"]
                 [venantius/accountant "0.2.4"
                  :exclusions [org.clojure/tools.reader]]
                 [cljs-ajax "0.8.0"]
                 [cljsjs/mustache "2.2.1-0"]
                 [commons-ui "0.1.0-SNAPSHOT"]
                 [org.clojure/core.async "0.4.500"
                  :Exclusions [org.clojure/tools.reader]]
                 [ring/ring-json "0.5.0"]
                 [clj-http "3.10.0"]
                 [com.h2database/h2 "1.4.199"]
                 [com.draines/postal "2.0.3"]
                 [org.clojure/core.match "0.3.0"]
                 [ragtime "0.8.0"]
                 [hikari-cp "2.9.0"]
                 [buddy "2.0.0" :exclusions [buddy/buddy-core]]
                 [buddy/buddy-sign "3.1.0"]
                 [org.slf4j/slf4j-simple "1.7.25"]
                 [re-frame "0.10.9"]
                 [prismatic/plumbing "0.5.5"]
                 [ring-middleware-format "0.7.4"]
                 [sablono "0.8.6"]
                 [binaryage/oops "0.7.0"]]

  :plugins [[lein-environ "1.1.0"]]

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

  :profiles {:dev {:repl-options {:init (start-server)}
                   :dependencies [[cider/piggieback "0.4.1"]
                                  [prone "1.6.4"]
                                  [com.bhauman/figwheel-main "0.2.3"]
                                  [com.bhauman/rebel-readline-cljs "0.1.4"]]

                   :source-paths ["env/dev/clj"]
                   :env {:dev true}}

             :uberjar {:source-paths ["env/prod/clj"]
                       :prep-tasks ["compile"]
                       :env {:production true}
                       :aot :all
                       :omit-source true}})
