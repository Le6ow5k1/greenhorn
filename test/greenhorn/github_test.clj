(ns greenhorn.github-test
  (:require [greenhorn.github :refer :all]
            [clojure.test :refer :all]
            [vcr-clj.core :refer [with-cassette]]))

(deftest diff-lock-files-from-repos-test
  (testing "happy path"
    (with-cassette :github/diff-lock-files [{:var #'clj-http.client/get}]
      (let [result (diff-lock-files-from-repos "rails/rails" "1784803" "rails/rails" "6a1c021")]
        (is (= result
               {"delayed_job_active_record"
                [{:version "4.1.1",
                  :remote "https://github.com/collectiveidea/delayed_job_active_record.git",
                  :revision "36f434c4fd660e8f11ce932be117e9c71dde7212",
                  :ref nil,
                  :branch nil}
                 {:version "4.1.1",:remote "https://rubygems.org/"}]
                "delayed_job" [{:version "4.1.2",
                                :remote "https://github.com/collectiveidea/delayed_job.git",
                                :revision "e3772d4f0c8470d0fcba00c86ca3bc4f5e876830",
                                :ref nil,
                                :branch nil}
                               {:version "4.1.2",:remote "https://rubygems.org/"}]}))))))

(deftest handle-pull-test
  (let [diff-spy (atom nil)]
    (with-redefs [greenhorn.github/create-or-update-pull-comment (fn [project pull-num diff] (reset! diff-spy diff))
                  greenhorn.db/add-merge-commit-to-pull (fn [& _])]
      (testing "when merge commit is present"
        (with-cassette :github/handle-pull [{:var #'clj-http.client/get}]
          (let [pull {:merge_commit_sha "fb4dc3224508ba55320ecc61bf7da5f1282d6873"
                      :number 26729
                      :base {:ref "c05a209fe975f8ba490ceddd6112b71c18c71cb7" :repo {:full_name "rails/rails"}}}
                expected-diff {"nokogiri" [{:version "1.6.8.1", :remote "https://rubygems.org/"} {:version "1.6.8", :remote "https://rubygems.org/"}],
                               "mysql2" [{:version "0.4.5", :remote "https://rubygems.org/"} {:version "0.4.4", :remote "https://rubygems.org/"}],
                               "pkg-config" [nil {:version "1.1.7", :remote "https://rubygems.org/"}],
                               "kindlerb" [{:version "1.0.1", :remote "https://rubygems.org/"} {:version "0.1.1", :remote "https://rubygems.org/"}]}]
            (handle-pull {} pull {:last_merge_commit_sha "foobar"})
            (is (= @diff-spy expected-diff))))))))
