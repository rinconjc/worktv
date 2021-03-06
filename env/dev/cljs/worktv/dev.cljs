(ns ^:figwheel-no-load worktv.dev
  (:require [worktv.core :as core]
            [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)

(figwheel/watch-and-reload
  :websocket-url "ws://localhost:3447/figwheel-ws"
  :jsload-callback core/mount-root)

(core/init!)
