(ns worktv.subs

  (:require [re-frame.subs :refer [reg-sub]]))

(reg-sub
 :current-page
 (fn [db _]
   (:current-page db)))
