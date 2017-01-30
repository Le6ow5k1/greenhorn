(ns greenhorn.github-test
  (:require [greenhorn.github :refer :all]
            [clojure.test :refer :all]
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
