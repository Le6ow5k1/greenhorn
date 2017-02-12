(ns greenhorn.github.api-test
  (:require [greenhorn.github.api :refer :all]
            [clojure.test :refer :all]
            [vcr-clj.core :refer [with-cassette]]))

(deftest compare-commits-test
  (with-cassette :github/api/compare-commits [{:var #'tentacles.repos/compare-commits}]
    (testing "when there are more commits then limit specified"
      (let [result (compare-commits "rails" "jbuilder" "v2.5.0" "v2.6.0")]
        (is (= result {:commits [
                                 {:url "https://github.com/rails/jbuilder/commit/003a369f7432c7173d00c453484ae11f1c952a15",
                                  :message "Version 2.6.0"}
                                 {:url "https://github.com/rails/jbuilder/commit/7a846a8b4229505e996de2bcac6d4309ff4e1752",
                                  :message "Bump up ruby versions for Travis"}
                                 {:url "https://github.com/rails/jbuilder/commit/c34503d59ff4e96a55c3e0645ae1e47b0b198055",
                                  :message "Update appraisals to use rails 5.0 release"}
                                 {:url "https://github.com/rails/jbuilder/commit/82bd4efa9c9d424959bef06f6461bd8c3b12e1b8",
                                  :message "Use native badge services vs shields.io"}
                                 {:url "https://github.com/rails/jbuilder/commit/d61e3354563863731bc1f358f495b1dbb7ae9d32",
                                  :message "fix #cache! with expires_in option (#325)"}
                                 {:url "https://github.com/rails/jbuilder/commit/83256f4d7e9211c9dc47972feaed7fd31e4f7cac",
                                  :message "DRY generated view files\n\n* Dry up generated json.jbuilder files\r\n\r\n* Partial should use attributes_list_with_timestamps\r\n\r\n* clean up whitespace"}
                                 {:url "https://github.com/rails/jbuilder/commit/da700c9afdd250ca6d3989cd8674da249ff3f8ad",
                                  :message "Follow Rails' convention"}
                                 {:url "https://github.com/rails/jbuilder/commit/83a682aeebde96c6ef02ce742c0b97dc393f5e22",
                                  :message ":heavy_check_mark: JSON syntax"}
                                 {:url "https://github.com/rails/jbuilder/commit/f39e8b89b584d3de1f28d86bb9ef4268fa61826a",
                                  :message "Add tests for fragment_cache_key and instrumentation"}
                                 {:url "https://github.com/rails/jbuilder/commit/ddb2985c33c65a3f4233eb8eec98c6d5fc4aaf27",
                                  :message "Split cache read/write methods and add instrumentation following ActionView's cache helpers"}],
                       :total 12}))))))

(deftest get-file-test
  (with-cassette :github/api/get-file [{:var #'clj-http.client/get}]
    (testing "successful request"
      (let [result (get-file "https://api.github.com/repos/rails/rails/contents/RAILS_VERSION")]
        (is (= result "5.1.0.alpha\n"))))

    (testing "unsuccessful request"
      (is (thrown? Throwable (get-file "https://api.github.com/repos/rails/rails/contents/missing_file"))))))
