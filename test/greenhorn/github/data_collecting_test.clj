(ns greenhorn.github.data-collecting-test
  (:require [greenhorn.github.data-collecting :refer :all]
            [clojure.test :refer :all]))

(deftest gems-data-test
  (testing "when gem has been added"
    (let [diffs {"activemodel" [nil {:version "3.1.0" :remote "https://rubygems.org/"}]}
          result (gems-data "rails" ["activemodel"] diffs)]
      (is (= result [{:name "activemodel"
                      :old-gem nil
                      :new-gem {:version "3.1.0" :remote "https://rubygems.org/"}
                      :old-gem-ref nil
                      :new-gem-ref "v3.1.0"
                      :status :added
                      :compare-str nil
                      :compare-url nil
                      :diff-commits []
                      :diff-behind-by nil
                      :downgraded? nil
                      :url "https://github.com/rails/activemodel"}]))))

  (testing "when gem has been deleted"
    (let [diffs {"activemodel" [{:version "3.1.0" :remote "https://rubygems.org/"} nil]}
          result (gems-data "rails" ["activemodel"] diffs)]
      (is (= result [{:name "activemodel"
                      :old-gem {:version "3.1.0" :remote "https://rubygems.org/"}
                      :new-gem nil
                      :old-gem-ref "v3.1.0"
                      :new-gem-ref nil
                      :status :deleted
                      :compare-str nil
                      :compare-url nil
                      :diff-commits []
                      :diff-behind-by nil
                      :downgraded? nil
                      :url "https://github.com/rails/activemodel"}]))))

  (testing "when gem has been updated"
    (testing "when gem is not in organization repositories"
      (let [diffs {"activemodel" [{:version "3.1.0" :remote "https://rubygems.org/"}
                                  {:version "3.2.0" :remote "https://rubygems.org/"}]}
            result (gems-data "rails" [] diffs)]
        (is (= result [{:name "activemodel"
                        :old-gem-ref "v3.1.0"
                        :new-gem-ref "v3.2.0"
                        :old-gem {:version "3.1.0" :remote "https://rubygems.org/"}
                        :new-gem {:version "3.2.0" :remote "https://rubygems.org/"}
                        :compare-str "v3.1.0...v3.2.0"
                        :compare-url nil
                        :status :updated
                        :diff-commits []
                        :diff-behind-by nil
                        :downgraded? nil
                        :url nil}]))))

    (testing "when gem is in organization repositories"
      (with-redefs [greenhorn.github.api/compare-commits
                    (fn [& args]
                      {:commits [{:html_url "http://url.com"
                                  :author {:avatar_url "https://avatars/1.gif"}
                                  :commit {:message "commit message"}}]})]
        (let [diffs {"activemodel" [{:version "3.1.0" :remote "https://rubygems.org/"}
                                    {:version "3.2.0" :remote "https://rubygems.org/"}]}
              result (gems-data "rails" ["activemodel"] diffs)]
          (is (= result [{:name "activemodel"
                          :old-gem-ref "v3.1.0"
                          :new-gem-ref "v3.2.0"
                          :old-gem {:version "3.1.0" :remote "https://rubygems.org/"}
                          :new-gem {:version "3.2.0" :remote "https://rubygems.org/"}
                          :compare-str "v3.1.0...v3.2.0"
                          :compare-url "https://github.com/rails/activemodel/compare/v3.1.0...v3.2.0"
                          :status :updated
                          :diff-commits [{:html_url "http://url.com"
                                          :author {:avatar_url "https://avatars/1.gif"}
                                          :commit {:message "commit message"}}]
                          :diff-behind-by nil
                          :downgraded? nil
                          :url "https://github.com/rails/activemodel"}])))))
    )
  )
