(ns greenhorn.gemfile-parsing-test
  (:require [greenhorn.gemfile-parsing :refer :all]
            [clojure.test :refer :all]))

(deftest parsing
  (testing "git section"
    (let [s "GIT
  remote: git://github.com/rails/rails.git
  revision: 325008e70fd57abaf80b172bd1ed451f4e6dd4ab
  branch: 3.1.13
  specs:
    actionmailer (3.1.12)
      actionpack (= 3.1.12)
      mail (~> 2.4.4)
    aasm (3.1.1)
    actionpack (3.1.12)
      activemodel (= 3.1.12)
      activesupport (= 3.1.12)
    active_hash (1.4.1)"]
      (is (=
           (vec (parse-lock-file s))
           [{:name "actionmailer"
             :version "3.1.12"
             :remote "git://github.com/rails/rails.git"
             :revision "325008e70fd57abaf80b172bd1ed451f4e6dd4ab"
             :ref nil
             :branch "3.1.13"}
            {:name "aasm"
             :version "3.1.1"
             :remote "git://github.com/rails/rails.git"
             :revision "325008e70fd57abaf80b172bd1ed451f4e6dd4ab"
             :ref nil
             :branch "3.1.13"}
            {:name "actionpack"
             :version "3.1.12"
             :remote "git://github.com/rails/rails.git"
             :revision "325008e70fd57abaf80b172bd1ed451f4e6dd4ab"
             :ref nil
             :branch "3.1.13"}
            {:name "active_hash"
             :version "1.4.1"
             :remote "git://github.com/rails/rails.git"
             :revision "325008e70fd57abaf80b172bd1ed451f4e6dd4ab"
             :ref nil
             :branch "3.1.13"}]
           ))))
  (testing "gem section"
    (let [s "GEM
  remote: https://rubygems.org/
  remote: https://gems.railsc.ru/
  remote: https://rails-assets.org/
  specs:
    aasm (3.1.1)
    active_attr (0.9.0)
      activemodel (>= 3.0.2, < 5.1)
      activesupport (>= 3.0.2, < 5.1)
    active_hash (1.4.1)
    active_support-lazy_load_patch (0.0.2)
      activesupport (~> 3.1.0)
    activerecord-postgres-hstore (0.7.8)
      activerecord (>= 3.1)
      pg-hstore (>= 1.1.5)
      rake"]
      (is (=
           (vec (parse-lock-file s))
           [{:name "aasm"
             :version "3.1.1"
             :remote "https://rubygems.org/, https://gems.railsc.ru/, https://rails-assets.org/"}
            {:name "active_attr"
             :version "0.9.0"
             :remote "https://rubygems.org/, https://gems.railsc.ru/, https://rails-assets.org/"}
            {:name "active_hash"
             :version "1.4.1"
             :remote "https://rubygems.org/, https://gems.railsc.ru/, https://rails-assets.org/"}
            {:name "active_support-lazy_load_patch"
             :version "0.0.2"
             :remote "https://rubygems.org/, https://gems.railsc.ru/, https://rails-assets.org/"}
            {:name "activerecord-postgres-hstore"
             :version "0.7.8"
             :remote "https://rubygems.org/, https://gems.railsc.ru/, https://rails-assets.org/"}]
           ))))
  )

(deftest diffing
  (testing "diff in GEM section by version"
    (let [old-lock "GEM
  remote: https://rubygems.org/
  specs:
    aasm (3.1.1)"
          new-lock "GEM
  remote: https://rubygems.org/
  specs:
    aasm (3.2.1)"]
      (is (= (diff-lock-files old-lock new-lock)
             {"aasm" [{:version "3.1.1"}
                      {:version "3.2.1"}]}))))

  (testing "diff in GIT section by revision"
    (let [old-lock "GIT
  remote: git://github.com/rails/rails.git
  revision: 325008e70fd57abaf80b172bd1ed451f4e6dd4ab
  specs:
    actionmailer (3.1.12)"
          new-lock "GIT
  remote: git://github.com/rails/rails.git
  revision: 131df504e315aaa72ba72f854485a642001c2cf4
  specs:
    actionmailer (3.1.12)"]
      (is (= (diff-lock-files old-lock new-lock)
             {"actionmailer" [{:version "3.1.12" :revision "325008e70fd57abaf80b172bd1ed451f4e6dd4ab"}
                              {:version "3.1.12" :revision "131df504e315aaa72ba72f854485a642001c2cf4"}]}))))

  (testing "gem removed and gem added"
    (let [old-lock "GIT
  remote: git@github.com:rails/actionmailer.git
  revision: 131df504e315aaa72ba72f854485a642001c2cf4
  ref: 131df504e315aaa72ba72f854485a642001c2cf4
  specs:
    actionmailer (3.1.12)
      class_logger (~> 1.0.1)"
          new-lock "GEM
  remote: https://rubygems.org/
  specs:
    activemodel (3.1.0)
      class_logger (~> 1.0.1)
      interactor (>= 3.0)
      postgresql_cursor (>= 0.6.1)"]
      (is (= (diff-lock-files old-lock new-lock)
             {"actionmailer" [{:version "3.1.12" :revision "131df504e315aaa72ba72f854485a642001c2cf4"}
                              nil]
              "activemodel" [nil
                             {:version "3.1.0"}]}))))

  (testing "diff in GEM and GIT section"
    (let [old-lock "GEM
  remote: https://rubygems.org/
  specs:
    actionmailer (3.1.0)"
          new-lock "GIT
  remote: git://github.com/rails/rails.git
  revision: 131df504e315aaa72ba72f854485a642001c2cf4
  specs:
    actionmailer (3.1.12)"]
      (is (= (diff-lock-files old-lock new-lock)
             {"actionmailer" [{:version "3.1.0"}
                              {:version "3.1.12" :revision "131df504e315aaa72ba72f854485a642001c2cf4"}]}))))

  (testing "diff in GIT and GEM section"
    (let [old-lock "GIT
  remote: git@github.com:rails/actionmailer.git
  revision: 131df504e315aaa72ba72f854485a642001c2cf4
  ref: 131df504e315aaa72ba72f854485a642001c2cf4
  specs:
    actionmailer (3.1.12)
      class_logger (~> 1.0.1)"
          new-lock "GEM
  remote: https://rubygems.org/
  specs:
    actionmailer (3.1.0)
      class_logger (~> 1.0.1)
      interactor (>= 3.0)
      postgresql_cursor (>= 0.6.1)"]
      (is (= (diff-lock-files old-lock new-lock)
             {"actionmailer" [{:version "3.1.12" :revision "131df504e315aaa72ba72f854485a642001c2cf4"}
                              {:version "3.1.0"}]}))))
  )
