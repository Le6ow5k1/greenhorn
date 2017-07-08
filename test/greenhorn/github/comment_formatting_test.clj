(ns greenhorn.github.comment-formatting-test
  (:require [greenhorn.github.comment-formatting :refer :all]
            [clojure.test :refer :all]
            [taoensso.timbre :as timbre]))

(deftest commit-to-markdown-test
  (testing "when message without body"
    (let [result (commit-to-markdown {:html_url "http://url.com"
                                      :author {:avatar_url "https://avatars/1.gif"}
                                      :commit {:message "Header of commit message"}})]
      (is (= result (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                         "[`Header of commit message`](http://url.com)")))))

  (testing "when message with body"
    (let [result (commit-to-markdown {:html_url "http://url.com"
                                      :author {:avatar_url "https://avatars/1.gif"}
                                      :commit {:message "Header of commit message\n\nBody of commit"}})]
      (is (= result (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                         "[`Header of commit message`](http://url.com)")))))

  (testing "when there is no blank line between header and body"
    (let [result (commit-to-markdown {:html_url "http://url.com"
                                      :author {:avatar_url "https://avatars/1.gif"}
                                      :commit {:message "Header of commit message\nBody of commit"}})]
      (is (= result (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                         "[`Header of commit message`](http://url.com)")))))

  (testing "when there is markdown code in header"
    (let [result (commit-to-markdown {:html_url "http://url.com"
                                      :author {:avatar_url "https://avatars/1.gif"}
                                      :commit {:message "Header `code` of commit message"}})]
      (is (= result (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                         "[`Header ``code`` of commit message`](http://url.com)")))))

  (testing "when message with a link to jira"
    (let [result (commit-to-markdown {:html_url "http://url.com"
                                      :author {:avatar_url "https://avatars/1.gif"}
                                      :commit {:message "Header of commit message\n\nBody of commit\nhttps://jira.com/browse/A4-18"}})]
      (is (= result (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                         "[`Header of commit message`](http://url.com) | [A4-18](https://jira.com/browse/A4-18)")))))

  (testing "when message with multiple links to jira separated by return"
    (let [message "Header of commit message\n\nBody of commit\nhttps://jira.com/browse/A4-18\nhttps://jira.com/browse/A5-18"
          result (commit-to-markdown {:html_url "http://url.com"
                                      :author {:avatar_url "https://avatars/1.gif"}
                                      :commit {:message message}})]
      (is (= result
             (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                  "[`Header of commit message`](http://url.com) | [A4-18](https://jira.com/browse/A4-18), [A5-18](https://jira.com/browse/A5-18)")))))

  (testing "when message with multiple links to jira separated by whitespace"
    (let [message "Header of commit message\n\nBody of commit\nhttps://jira.com/browse/A4-18 https://jira.com/browse/A5-18"
          result (commit-to-markdown {:html_url "http://url.com"
                                      :author {:avatar_url "https://avatars/1.gif"}
                                      :commit {:message message}})]
      (is (= result
             (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                  "[`Header of commit message`](http://url.com) | [A4-18](https://jira.com/browse/A4-18), [A5-18](https://jira.com/browse/A5-18)")))))

  (testing "when avatar_url is missing"
    (let [result (commit-to-markdown {:html_url "http://url.com"
                                      :author {:avatar_url nil}
                                      :commit {:message "Header of commit message"}})]
      (is (= result (str "  <img height=\"16\" src=\"https://i2.wp.com/assets-cdn.github.com/images/gravatars/gravatar-user-420.png?v=3&amp;s=32\" width=\"16\"> "
                         "[`Header of commit message`](http://url.com)")))))
  )

(deftest commits-to-markdown-test
  (testing "when there are less messages then total commits"
    (let [result (commits-to-markdown [{:html_url "http://url.com"
                                        :author {:avatar_url "https://avatars/1.gif"}
                                        :commit {:message "message 1"}}
                                       {:html_url "http://url.com"
                                        :author {:avatar_url "https://avatars/1.gif"}
                                        :commit {:message "message 2"}}] 2)]
      (is (= result
             (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                  "[`message 1`](http://url.com)\n"
                  "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                  "[`message 2`](http://url.com)")))))

  (testing "when there are more messages then total commits"
    (let [result (commits-to-markdown [{:html_url "http://url.com"
                                        :author {:avatar_url "https://avatars/1.gif"}
                                        :commit {:message "message 1"}}
                                       {:html_url "http://url.com"
                                        :author {:avatar_url "https://avatars/1.gif"}
                                        :commit {:message "message 2"}}] 4)]
      (is (= result
             (str "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                  "[`message 1`](http://url.com)\n"
                  "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                  "[`message 2`](http://url.com)\n"
                  "  ... and 2 more significant commit(s)")))))

  (testing "when there are no messages"
    (let [result (commits-to-markdown [] 0)]
      (is (= result ""))))
  )

(deftest diff-to-comment-test
  (with-redefs [greenhorn.github.api/compare-commits (fn [& args]
                                                       {:commits [{:html_url "http://url.com"
                                                                   :author {:avatar_url "https://avatars/1.gif"}
                                                                   :commit {:message "commit message"}}] :total 1})]
    (testing "when gem is updated"
      (def updated-diff ["rails" [{:version "3.1.0"
                                   :remote "https://rubygems.org/"}
                                  {:version "3.1.12"
                                   :remote "git://github.com/rails/rails.git"
                                   :revision "131df504e315aaa72ba72f854485a642001c2cf4"
                                   :ref nil
                                   :branch nil}]])

      (testing "when gem repo exists in organization"
        (let [result (diff-to-comment "rails" true updated-diff)]
          (is (= result
                 (str "**rails** has been updated [v3.1.0...131df50](https://github.com/rails/rails/compare/v3.1.0...131df50)\n"
                      "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                      "[`commit message`](http://url.com)")))))

      (testing "when gem repo exist in organization and both remotes pointing to github"
        (let [diff (assoc-in updated-diff [1 0 :remote] "git@github.com:rails/rails.git")
              result (diff-to-comment "rails" true diff)]
          (is (= result
                 (str "**rails** has been updated [v3.1.0...131df50](https://github.com/rails/rails/compare/v3.1.0...131df50)\n"
                      "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                      "[`commit message`](http://url.com)")))))

      (testing "when gem repo doesn't exist in organization and remote not pointing to github"
        (let [result (diff-to-comment "rails" false updated-diff)]
          (is (= result
                 "**rails** has been updated v3.1.0...131df50"))))
      )

    (testing "when gem is added"
      (def added-diff ["rails" [nil
                                {:version "3.1.0"
                                 :remote "https://rubygems.org/"}]])

      (let [result (diff-to-comment "rails" true added-diff)]
        (is (= result
               "**rails** has been added [v3.1.0](https://github.com/rails/rails/tree/v3.1.0)")))

      (testing "when gem doesn't exist in organization"
        (let [result (diff-to-comment "rails" nil added-diff)]
          (is (= result "**rails** has been added v3.1.0"))))
      )
    )

  (testing "when there are no compare commits"
    (with-redefs [greenhorn.github.api/compare-commits (fn [& args] {:commits [] :total 0 :status "ahead"})]
      (let [result (diff-to-comment "rails" true updated-diff)]
        (is (= result (str "**rails** has been updated [v3.1.0...131df50](https://github.com/rails/rails/compare/v3.1.0...131df50)"
                           "\n:exclamation: no commits found for diff"))))))

  (testing "when there are no compare commits and diff's head is behind base"
    (with-redefs [greenhorn.github.api/compare-commits (fn [& args] {:commits [] :total 0 :status "behind"})]
      (let [result (diff-to-comment "rails" true updated-diff)]
        (is (= result (str "**rails** has been updated [v3.1.0...131df50](https://github.com/rails/rails/compare/v3.1.0...131df50)"
                           "\n:arrow_down: this is a downgrade"))))))
  )

(deftest diffs-to-comment-test
  (def diffs {"rails" [{:version "3.1.0"
                        :remote "https://rubygems.org/"}
                       {:version "3.1.12"
                        :remote "git://github.com/rails/rails.git"
                        :revision "131df504e315aaa72ba72f854485a642001c2cf4"
                        :ref nil
                        :branch nil}]
              "jbuilder" [{:version "2.5.1"
                           :remote "git://github.com/rails/jbuilder.git"
                           :revision "e0986b357ecd2062c38fa6f7215bbdc494396803"
                           :ref nil
                           :branch nil}
                          {:version "2.6.1"
                           :remote "git://github.com/rails/jbuilder.git"
                           :revision "131df504e315aaa72ba72f854485a642001c2cf4"
                           :ref nil
                           :branch nil}]
              "puma" [nil
                      {:version "3.6.2"
                       :remote "https://rubygems.org/"}]})

  (with-redefs [greenhorn.github.api/compare-commits (fn [& args]
                                                       {:commits [{:html_url "http://url.com"
                                                                   :author {:avatar_url "https://avatars/1.gif"}
                                                                   :commit {:message "commit message"}}] :total 1})]
    (testing "happy path"
      (let [result (diffs-to-comment "rails" ["rails" "jbuilder"] diffs)]
        (is (= result
               (str "- **jbuilder** has been updated [e0986b3...131df50](https://github.com/rails/jbuilder/compare/e0986b3...131df50)\n"
                    "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                    "[`commit message`](http://url.com)\n"
                    "- **puma** has been added v3.6.2\n"
                    "- **rails** has been updated [v3.1.0...131df50](https://github.com/rails/rails/compare/v3.1.0...131df50)\n"
                    "  <img height=\"16\" src=\"https://avatars/1.gif?v=3&amp;s=32\" width=\"16\"> "
                    "[`commit message`](http://url.com)"))))))
  )
