(ns greenhorn.github.api-test
  (:require [greenhorn.github.api :refer :all]
            [clojure.test :refer :all]
            [vcr-clj.core :refer [with-cassette]]))

(deftest compare-commit-messages-test
  (with-cassette :github [{:var #'tentacles.repos/compare-commits}]
    (testing "when there are more commits then limit specified"
      (let [result (compare-commit-messages "rails" "jbuilder" "v2.5.0" "v2.6.0")]
        (is (= result {:messages ["Remove relic and confusing  argument"
                                  "Use fragment_cache_key when available so caches are properly expired"
                                  "Split cache read/write methods and add instrumentation following ActionView's cache helpers"
                                  "Add tests for fragment_cache_key and instrumentation"
                                  "Merge pull request #341 from javan/respect-fragment-cache-keys\n\nFix fragment_cache_key cache invalidation"
                                  "Merge pull request #339 from tduek/cleanup-confusing-code\n\nCleanup: remove relic and confusing 'app' argument"
                                  ":heavy_check_mark: JSON syntax"
                                  "Follow Rails' convention"
                                  "DRY generated view files\n\n* Dry up generated json.jbuilder files\r\n\r\n* Partial should use attributes_list_with_timestamps\r\n\r\n* clean up whitespace"
                                  "fix #cache! with expires_in option (#325)"]
                       :total_commits 14}))))))
