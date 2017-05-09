(ns worktv.utils-test
  (:require [cljs.test :refer-macros [deftest is run-tests]]
            [worktv.utils :refer [date-from-name parse-date-exp]]))

(deftest test-date-expressions
  (is (instance? js/Date (date-from-name "now")))
  (is (some? (parse-date-exp "now"))))

(run-tests)
