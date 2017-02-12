(ns greenhorn.github.comment-formatting-test
  (:require [greenhorn.github.comment-formatting :refer :all]
            [clojure.test :refer :all]))

(deftest commit-to-markdown-test
  (testing "when message without body"
    (let [result (commit-to-markdown {:url "http://url.com" :message "Header of commit message"})]
      (is (= result "  - [`Header of commit message`](http://url.com)"))))

  (testing "when message with body"
    (let [result (commit-to-markdown {:url "http://url.com" :message "Header of commit message\n\nBody of commit"})]
      (is (= result "  - [`Header of commit message`](http://url.com)"))))

  (testing "when message with a link to jira"
    (let [result (commit-to-markdown {:url "http://url.com" :message "Header of commit message\n\nBody of commit\nhttps://jira.com/browse/A4-18"})]
      (is (= result "  - [`Header of commit message`](http://url.com) [A4-18](https://jira.com/browse/A4-18)"))))

  (testing "when message with links to jira"
    (let [message "Header of commit message\n\nBody of commit\nhttps://jira.com/browse/A4-18\nhttps://jira.com/browse/A5-18"
          result (commit-to-markdown {:url "http://url.com" :message message})]
      (is (= result
             "  - [`Header of commit message`](http://url.com) [A4-18](https://jira.com/browse/A4-18) [A5-18](https://jira.com/browse/A5-18)"))))
  )

(deftest commits-to-markdown-test
  (testing "when there are less messages then total commits"
    (let [result (commits-to-markdown [{:url "http://url.com" :message "message 1"}
                                       {:url "http://url.com" :message "message 2"}] 2)]
      (is (= result
             (str "  - [`message 1`](http://url.com)\n"
                  "  - [`message 2`](http://url.com)")))))

  (testing "when there are more messages then total commits"
    (let [result (commits-to-markdown [{:url "http://url.com" :message "message 1"}
                                       {:url "http://url.com" :message "message 2"}] 4)]
      (is (= result
             (str "  - [`message 1`](http://url.com)\n"
                  "  - [`message 2`](http://url.com)\n"
                  "  - ... and 2 more significant commits")))))

  (testing "when there are no messages"
    (let [result (commits-to-markdown [] 0)]
      (is (= result ""))))
  )

(deftest diff-to-markdown-test
  (with-redefs [greenhorn.github.api/compare-commits (fn [& args]
                                                       {:commits [{:url "http://url.com" :message "commit message"}] :total 1})]
    (testing "when gem is updated"
      (def updated-diff ["rails" [{:version "3.1.0"
                                   :remote "https://rubygems.org/"}
                                  {:version "3.1.12"
                                   :remote "git://github.com/rails/rails.git"
                                   :revision "131df504e315aaa72ba72f854485a642001c2cf4"
                                   :ref nil
                                   :branch nil}]])

      (testing "when gem repo exists in organization"
        (let [result (diff-to-markdown "rails" true updated-diff)]
          (is (= result
                 (str "**rails** has been updated [v3.1.0...131df50](https://github.com/rails/rails/compare/v3.1.0...131df50)\n"
                      "  - [`commit message`](http://url.com)"))))

        (testing "when there are no commit messages for compare"
          (with-redefs [greenhorn.github.api/compare-commits (fn [& args] {:commits [] :total 0})]
            (let [result (diff-to-markdown "rails" true updated-diff)]
              (is (= result
                     (str "**rails** has been updated [v3.1.0...131df50](https://github.com/rails/rails/compare/v3.1.0...131df50)"))))))
        )

      (testing "when gem repo doesn't exist in organization and remote pointing to github"
        (let [diff (assoc-in updated-diff [1 0 :remote] "git://github.com/rails/rails.git")
              result (diff-to-markdown "rails" false diff)]
          (is (= result
                 (str "**rails** has been updated [v3.1.0...131df50](https://github.com/rails/rails/compare/v3.1.0...131df50)\n"
                      "  - [`commit message`](http://url.com)")))))

      (testing "when gem repo doesn't exist in organization and remote not pointing to github"
        (let [result (diff-to-markdown "rails" false updated-diff)]
          (is (= result
                 "**rails** has been updated v3.1.0...131df50"))))
      )

    (testing "when gem is added"
      (def added-diff ["rails" [nil
                                {:version "3.1.0"
                                 :remote "https://rubygems.org/"}]])

      (let [result (diff-to-markdown "rails" true added-diff)]
        (is (= result
               "**rails** has been added [v3.1.0](https://github.com/rails/rails/tree/v3.1.0)")))

      (testing "when gem doesn't exist in organization"
        (let [result (diff-to-markdown "rails" nil added-diff)]
          (is (= result "**rails** has been added v3.1.0"))))
      )
    )
  )

(deftest diffs-to-markdown-test
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
                                                       {:commits [{:url "http://url.com" :message "commit message"}] :total 1})]
    (testing "happy path"
      (let [result (diffs-to-markdown "rails" ["rails" "jbuilder"] diffs)]
        (is (= result
               (str "- **jbuilder** has been updated [e0986b3...131df50](https://github.com/rails/jbuilder/compare/e0986b3...131df50)\n"
                    "  - [`commit message`](http://url.com)\n"
                    "- **puma** has been added v3.6.2\n"
                    "- **rails** has been updated [v3.1.0...131df50](https://github.com/rails/rails/compare/v3.1.0...131df50)\n"
                    "  - [`commit message`](http://url.com)"))))))
  )
