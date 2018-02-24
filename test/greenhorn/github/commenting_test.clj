(ns greenhorn.github.commenting-test
  (:require [greenhorn.github.commenting :refer :all]
            [clojure.test :refer :all]
            [taoensso.timbre :as timbre]))

(deftest commit-to-markdown-test
  (testing "when message without body"
    (let [result (commit-to-markdown {:html_url "http://url.com"
                                      :author {:avatar_url "https://avatars/1.gif"}
                                      :commit {:message "Header of commit message"}})]
      (is (= result (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                         "[`` Header of commit message ``](http://url.com)")))))

  (testing "when message with body"
    (let [result (commit-to-markdown {:html_url "http://url.com"
                                      :author {:avatar_url "https://avatars/1.gif"}
                                      :commit {:message "Header of commit message\n\nBody of commit"}})]
      (is (= result (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                         "[`` Header of commit message ``](http://url.com)")))))

  (testing "when there is no blank line between header and body"
    (let [result (commit-to-markdown {:html_url "http://url.com"
                                      :author {:avatar_url "https://avatars/1.gif"}
                                      :commit {:message "Header of commit message\nBody of commit"}})]
      (is (= result (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                         "[`` Header of commit message ``](http://url.com)")))))

  (testing "when there is markdown code in header"
    (let [result (commit-to-markdown {:html_url "http://url.com"
                                      :author {:avatar_url "https://avatars/1.gif"}
                                      :commit {:message "Header `code` of commit message"}})]
      (is (= result (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                         "[`` Header `code` of commit message ``](http://url.com)")))))

  (testing "when message with a link to jira"
    (let [result (commit-to-markdown {:html_url "http://url.com"
                                      :author {:avatar_url "https://avatars/1.gif"}
                                      :commit {:message "Header of commit message\n\nBody of commit\nhttps://jira.com/browse/A4-18"}})]
      (is (= result (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                         "[`` Header of commit message ``](http://url.com) • [A4-18](https://jira.com/browse/A4-18)")))))

  (testing "when message with multiple links to jira separated by return"
    (let [message "Header of commit message\n\nBody of commit\nhttps://jira.com/browse/A4-18\nhttps://jira.com/browse/A5-18"
          result (commit-to-markdown {:html_url "http://url.com"
                                      :author {:avatar_url "https://avatars/1.gif"}
                                      :commit {:message message}})]
      (is (= result
             (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                  "[`` Header of commit message ``](http://url.com) • [A4-18](https://jira.com/browse/A4-18), [A5-18](https://jira.com/browse/A5-18)")))))

  (testing "when message with multiple links to jira separated by whitespace"
    (let [message "Header of commit message\n\nBody of commit\nhttps://jira.com/browse/A4-18 https://jira.com/browse/A5-18"
          result (commit-to-markdown {:html_url "http://url.com"
                                      :author {:avatar_url "https://avatars/1.gif"}
                                      :commit {:message message}})]
      (is (= result
             (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                  "[`` Header of commit message ``](http://url.com) • [A4-18](https://jira.com/browse/A4-18), [A5-18](https://jira.com/browse/A5-18)")))))

  (testing "when avatar_url is missing"
    (let [result (commit-to-markdown {:html_url "http://url.com"
                                      :author {:avatar_url nil}
                                      :commit {:message "Header of commit message"}})]
      (is (= result (str "  <img height=\"16\" src=\"https://i2.wp.com/assets-cdn.github.com/images/gravatars/gravatar-user-420.png?v=3&amp;s=32\" width=\"16\"> "
                         "[`` Header of commit message ``](http://url.com)")))))
  )

(deftest commits-to-markdown-test
  (testing "when number of commits don't exceed limit"
    (let [result (commits-to-markdown [{:html_url "http://url.com"
                                        :author {:avatar_url "https://avatars/1.gif"}
                                        :commit {:message "message 1"}}
                                       {:html_url "http://url.com"
                                        :author {:avatar_url "https://avatars/1.gif"}
                                        :commit {:message "message 2"}}])]
      (is (= result
             (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                  "[`` message 1 ``](http://url.com)\n"
                  "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                  "[`` message 2 ``](http://url.com)")))))

  (testing "when number of commits exceed limit"
    (with-redefs [greenhorn.github.commenting/visible-commits-limit 1]
      (let [result (commits-to-markdown [{:html_url "http://url.com"
                                          :author {:avatar_url "https://avatars/1.gif"}
                                          :commit {:message "message 1"}}
                                         {:html_url "http://url.com"
                                          :author {:avatar_url "https://avatars/1.gif"}
                                          :commit {:message "message 2"}}])]
        (is (= result
               (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                    "[`` message 1 ``](http://url.com)\n"
                    "  ... and 1 more significant commit(s)"))))))

  (testing "when there are no messages"
    (let [result (commits-to-markdown [])]
      (is (= result ""))))
  )

(deftest gem-data-to-comment-test
  (testing "when gem updated"
    (def gem-data {:name "rails"
                   :status :updated
                   :compare-str "v3.1.0...131df50"
                   :compare-url "https://github.com/rails/rails/compare/v3.1.0...131df50"
                   :diff-commits [{:html_url "http://url.com"
                                   :author {:avatar_url "https://avatars/1.gif"}
                                   :commit {:message "commit message"}}]
                   :url "https://github.com/rails/rails"})

    (let [result (gem-data-to-comment gem-data)]
      (is (= result
             (str "**rails** has been updated [v3.1.0...131df50](https://github.com/rails/rails/compare/v3.1.0...131df50)\n"
                  "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                  "[`` commit message ``](http://url.com)"))))

    (testing "when there are no diff-commits"
      (let [gem-data-with-no-commits (assoc gem-data :diff-commits [])
            result (gem-data-to-comment gem-data-with-no-commits)]
        (is (= result (str "**rails** has been updated [v3.1.0...131df50](https://github.com/rails/rails/compare/v3.1.0...131df50)\n"
                           ":confused: no commits found for diff")))))

    (testing "when gem has been downgraded"
      (let [gem-data-downgraded (assoc gem-data :diff-behind-by 2)
            result (gem-data-to-comment gem-data-downgraded)]
        (is (= result (str "**rails** has been updated [v3.1.0...131df50](https://github.com/rails/rails/compare/v3.1.0...131df50)\n"
                           ":open_mouth: 2 commits are missing")))))
    )

  (testing "when gem is added"
    (def gem-data {:name "rails"
                   :old-gem-ref nil
                   :new-gem-ref "v3.1.0"
                   :status :added
                   :compare-str nil
                   :compare-url nil
                   :diff-commits []
                   :url "https://github.com/rails/rails"})

    (let [result (gem-data-to-comment gem-data)]
      (is (= result
             "**rails** has been added [v3.1.0](https://github.com/rails/rails/tree/v3.1.0)")))

    (testing "when gem doesn't have an url"
      (let [gem-data-without-url (assoc gem-data :url nil)
            result (gem-data-to-comment gem-data-without-url)]
        (is (= result "**rails** has been added v3.1.0")))))
  )

(deftest gems-data-to-comment-test
  (def gems-data [{
                   :name "rails"
                   :status :updated
                   :compare-str "v3.1.0...131df50"
                   :compare-url "https://github.com/rails/rails/compare/v3.1.0...131df50"
                   :diff-commits [{:html_url "http://url.com"
                                   :author {:avatar_url "https://avatars/1.gif"}
                                   :commit {:message "commit message"}}]
                   :url "https://github.com/rails/rails"
                   }
                  {
                   :name "jbuilder"
                   :status :updated
                   :compare-str "e0986b3...131df50"
                   :compare-url "https://github.com/rails/jbuilder/compare/e0986b3...131df50"
                   :diff-commits [{:html_url "http://url.com"
                                   :author {:avatar_url "https://avatars/1.gif"}
                                   :commit {:message "commit message"}}]
                   :url "https://github.com/rails/jbuilder"
                   }
                  {
                   :name "puma"
                   :status :added
                   :old-gem-ref nil
                   :new-gem-ref "v3.6.2"
                   :compare-str nil
                   :compare-url nil
                   :diff-commits []
                   :url nil
                   }])

    (testing "happy path"
      (let [result (gems-data-to-comment gems-data)]
        (is (= result
               (str "- **jbuilder** has been updated [e0986b3...131df50](https://github.com/rails/jbuilder/compare/e0986b3...131df50)\n"
                    "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                    "[`` commit message ``](http://url.com)\n"
                    "- **puma** has been added v3.6.2\n"
                    "- **rails** has been updated [v3.1.0...131df50](https://github.com/rails/rails/compare/v3.1.0...131df50)\n"
                    "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                    "[`` commit message ``](http://url.com)")))))
  )
