(ns greenhorn.github-test
  (:require [greenhorn.github :refer :all]
            [clojure.test :refer :all]
            [clj-http.fake :refer :all]
            [taoensso.timbre :as timbre]
            [vcr-clj.core :refer [with-cassette]]))

(deftest diff-lock-files-from-repos-test
  (testing "happy path"
      (with-cassette :github [{:var #'clj-http.client/get}]
        (let [result (diff-lock-files-from-repos "Le6ow5k1/greenhorn-example" "master" "Le6ow5k1/greenhorn-example" "exp2")]
          (is (= result
                 {"jquery-rails" [nil
                                  {:version "4.2.1", :remote "https://rubygems.org/"}],
                  "puma" [{:version "3.6.0", :remote "https://rubygems.org/"}
                          {:version "3.6.2", :remote "https://rubygems.org/"}],
                  "jbuilder" [{:version "2.6.0",
                               :remote "git@github.com:rails/jbuilder.git",
                               :revision "554b64822357a283ef6f2385f7b6b9c8ad64dda3",
                               :ref "554b648",
                               :branch nil}
                              {:version "2.6.1", :remote "https://rubygems.org/"}],
                  "spring" [{:version "2.0.0", :remote "https://rubygems.org/"}
                            nil]}))))))

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
