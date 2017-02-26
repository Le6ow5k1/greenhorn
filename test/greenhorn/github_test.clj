(ns greenhorn.github-test
  (:require [greenhorn.github :refer :all]
            [greenhorn.gemfile-parsing :refer :all]
            [clojure.test :refer :all]
            [vcr-clj.core :refer [with-cassette]]))

(defmethod assert-expr 'equal-gem-diffs? [msg form]
  (let [gem-diffs (nth form 1)
        expected-diffs (nth form 2)]
    `(let [filter-falsey-from-map# #(some->> % (filter second) (into {}))
           actual-diffs# (mapv (fn [diff#]
                                 (-> (into {} diff#)
                                     (update-in [:base-gem] (comp (fn [m#] (dissoc m# :name)) filter-falsey-from-map#))
                                     (update-in [:head-gem] (comp (fn [m#] (dissoc m# :name)) filter-falsey-from-map#))))
                               ~gem-diffs)
           result# (= ~expected-diffs actual-diffs#)]
       (if result#
         (do-report {:type :pass, :message ~msg,
                     :expected ~expected-diffs, :actual actual-diffs#})
         (do-report {:type :fail, :message ~msg,
                     :expected ~expected-diffs, :actual actual-diffs#}))
       result#)))

(deftest diff-lock-files-from-repos-test
  (testing "happy path"
    (with-cassette :github/diff-lock-files [{:var #'clj-http.client/get}]
      (let [result (diff-lock-files-from-repos "rails/rails" "1784803" "rails/rails" "6a1c021")]
        (is (= result
               [#greenhorn.gemfile_parsing.GemDiff{:name "delayed_job_active_record"
                                                   :base-gem #greenhorn.gemfile_parsing.Gem{:name "delayed_job_active_record"
                                                                                            :version "4.1.1"
                                                                                            :remote "https://github.com/collectiveidea/delayed_job_active_record.git"
                                                                                            :revision "36f434c4fd660e8f11ce932be117e9c71dde7212"
                                                                                            :ref nil
                                                                                            :branch nil}
                                                   :head-gem #greenhorn.gemfile_parsing.Gem{:name "delayed_job_active_record"
                                                                                            :version "4.1.1"
                                                                                            :remote "https://rubygems.org/"
                                                                                            :revision nil
                                                                                            :ref nil
                                                                                            :branch nil}}
                #greenhorn.gemfile_parsing.GemDiff{:name "delayed_job"
                                                   :base-gem #greenhorn.gemfile_parsing.Gem{:name "delayed_job"
                                                                                            :version "4.1.2"
                                                                                            :remote "https://github.com/collectiveidea/delayed_job.git"
                                                                                            :revision "e3772d4f0c8470d0fcba00c86ca3bc4f5e876830"
                                                                                            :ref nil
                                                                                            :branch nil}
                                                   :head-gem #greenhorn.gemfile_parsing.Gem{:name "delayed_job"
                                                                                            :version "4.1.2"
                                                                                            :remote "https://rubygems.org/"
                                                                                            :revision nil
                                                                                            :ref nil
                                                                                            :branch nil}}]))))))

(deftest handle-pull-test
  (let [diff-spy (atom nil)]
    (with-redefs [greenhorn.github/create-or-update-pull-comment (fn [project pull-num diff] (reset! diff-spy diff))
                  greenhorn.db/update-pull (fn [project-id pull-num attra])]
      (testing "when merge commit is present"
        (with-cassette :github/handle-pull [{:var #'clj-http.client/get}]
          (let [pull {:merge_commit_sha "fb4dc3224508ba55320ecc61bf7da5f1282d6873"
                      :number 26729
                      :base {:ref "c05a209fe975f8ba490ceddd6112b71c18c71cb7" :repo {:full_name "rails/rails"}}}
                expected-diff [{:name "nokogiri"
                                :base-gem {:version "1.6.8.1" :remote "https://rubygems.org/"}
                                :head-gem {:version "1.6.8" :remote "https://rubygems.org/"}}
                               {:name "mysql2"
                                :base-gem {:version "0.4.5" :remote "https://rubygems.org/"}
                                :head-gem {:version "0.4.4" :remote "https://rubygems.org/"}}
                               {:name "pkg-config"
                                :base-gem nil
                                :head-gem {:version "1.1.7" :remote "https://rubygems.org/"}}
                               {:name "kindlerb"
                                :base-gem {:version "1.0.1" :remote "https://rubygems.org/"}
                                :head-gem {:version "0.1.1" :remote "https://rubygems.org/"}}]]
            (handle-pull {} pull {:last_merge_commit_sha "foobar"})
            (is (equal-gem-diffs? @diff-spy expected-diff))))))))


[{:name "nokogiri"
  :base-gem {:version "1.6.8.1" :remote "https://rubygems.org/"}
  :head-gem {:version "1.6.8" :remote "https://rubygems.org/"}}
 {:name "mysql2"
  :base-gem {:version "0.4.5" :remote "https://rubygems.org/"}
  :head-gem {:version "0.4.4" :remote "https://rubygems.org/"}}
 {:name "pkg-config"
  :base-gem nil
  :head-gem {:version "1.1.7" :remote "https://rubygems.org/"}}
 {:name "kindlerb"
  :base-gem {:version "1.0.1" :remote "https://rubygems.org/"}
  :head-gem {:version "0.1.1" :remote "https://rubygems.org/"}}]

[{:name "nokogiri"
  :base-gem {:name "nokogiri" :version "1.6.8.1" :remote "https://rubygems.org/"}
  :head-gem {:name "nokogiri" :version "1.6.8" :remote "https://rubygems.org/"}}
 {:name "mysql2"
  :base-gem {:name "mysql2" :version "0.4.5" :remote "https://rubygems.org/"}
  :head-gem {:name "mysql2" :version "0.4.4" :remote "https://rubygems.org/"}}
 {:name "pkg-config"
  :base-gem {}
  :head-gem {:name "pkg-config" :version "1.1.7" :remote "https://rubygems.org/"}}
 {:name "kindlerb"
  :base-gem {:name "kindlerb" :version "1.0.1" :remote "https://rubygems.org/"}
  :head-gem {:name "kindlerb" :version "0.1.1" :remote "https://rubygems.org/"}}]
