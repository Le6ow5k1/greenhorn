(ns greenhorn.github-test
  (:require [greenhorn.github :refer :all]
            [clojure.test :refer :all]
            [clj-http.fake :refer :all]
            [taoensso.timbre :as timbre]
            [greenhorn.github :as github]))

(deftest diff-lock-files-from-repos-test
  (testing "happy path"
    (let [api-url "https://api.github.com/"
          base-lock "GEM
  remote: https://rubygems.org/
  specs:
    actionmailer (3.1.0)"
           head-lock "GIT
  remote: git://github.com/rails/rails.git
  revision: 131df504e315aaa72ba72f854485a642001c2cf4
  specs:
    actionmailer (3.1.12)"]
      (with-fake-routes-in-isolation {
                                      (str api-url "repos/gem/contents/Gemfile.lock?ref=master")
                                      (fn [request] {:status 200 :headers {} :body base-lock})
                                      (str api-url "repos/gem/contents/Gemfile.lock?ref=develop")
                                      (fn [request] {:status 200 :headers {} :body head-lock})
                                      }

          (let [result (github/diff-lock-files-from-repos "gem" "master" "gem" "develop")]
            (is (= result {"actionmailer"
<<<<<<< HEAD
                           [{:version "3.1.0" :remote "https://rubygems.org/"}
                            {:version "3.1.12" :remote "git://github.com/rails/rails.git" :revision "131df504e315aaa72ba72f854485a642001c2cf4"}]}))))
=======
                           [{:version "3.1.0"}
                            {:version "3.1.12", :revision "131df504e315aaa72ba72f854485a642001c2cf4"}]}))))
>>>>>>> 6d734c0... Add test for github ns
          )))