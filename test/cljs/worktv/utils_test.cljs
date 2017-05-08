(ns worktv.utils-test
  (:require [cljs.test :refer-macros [deftest is run-tests]]
            [worktv.utils :refer [date-from-name format-date parse-date-exp]]))

(deftest date-functions
  (is (instance? js/Date (date-from-name "now")))
  (is (re-matches #"\d{4}-\d{2}-\d{2}" (format-date (js/Date.) "yyyy-MM-dd"))))

(deftest date-expressions
  (is (some? (parse-date-exp "now"))))

(run-tests)
