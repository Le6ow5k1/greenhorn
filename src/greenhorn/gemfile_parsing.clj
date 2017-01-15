(ns greenhorn.gemfile-parsing
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [clojure.data :as data]))

(def ^:private specs-section-re
  #"(?:\s{4}((?:\w|-|\.)+)\s\(((?:\w|\.)+)\))+\n?(?:\s{6}.*)*")
(def ^:private git-section-re
  #"\s{2}remote:\s(.+)\n\s{2}revision:\s(.+)\n(?:\s{2}ref:\s(.+))?\n?(?:\s{2}branch:\s(.+))?")

(defn- parse-specs-section [section additional-props]
  (let [matches (re-seq specs-section-re section)]
    (map (fn [[_ name version]] (merge {:name name :version version} additional-props)) matches)))

(defn- parse-git-section [section]
  (let [[_ remote revision ref branch] (re-find git-section-re section)]
    (parse-specs-section section {:remote remote :revision revision :ref ref :branch branch})))

(defn- parse-path-section [section]
  (let [[_ remote] (re-find #"\s{2}remote:\s(.+)" section)]
    (parse-specs-section section {:remote remote})))

(defn- parse-gem-section [section]
  (let [matches (re-seq #"(?:\s{2}remote:\s(.+).*)+" section)]
    (parse-specs-section section {:remote (str/join ", " (map second matches))})))

(defn- parse-section [section]
  (cond
    (str/starts-with? section "GIT") (parse-git-section section)
    (str/starts-with? section "PATH") (parse-path-section section)
    (str/starts-with? section "GEM") (parse-gem-section section)))

(defn parse-lock-file [file-name-or-content]
  (let [file-contents (if (str/ends-with? file-name-or-content ".lock")
                        (slurp file-name-or-content)
                        file-name-or-content)
        sections (str/split file-contents #"\n\n")]
    (->> sections (map parse-section) (reduce concat))))

(defn- select-diff-related-keys [[gem]]
  (select-keys gem [:revision :version :name]))

(defn diff-lock-files
  "Given two lock files (file contents or paths to files) finds differences between gems.

  Example outputs
    when gem is added
      => {\"activemodel\" [nil {:version \"3.1.0\" :remote \"https://rubygems.org/\"}]}
    when gem is removed
      => {\"activemodel\" [{:version \"3.1.0\" :remote \"https://rubygems.org/\"} nil]}
    when gem version is updated
      => {\"activemodel\" [{:version \"3.1.0\" :remote \"https://rubygems.org/\"}
                           {:version \"4.0.0\" :remote \"https://rubygems.org/\"}]}
  "
  [old-lock new-lock]
  (let [parsed-old (->> old-lock parse-lock-file (group-by :name))
        parsed-new (->> new-lock parse-lock-file (group-by :name))
        old-gemset (->> parsed-old vals (map select-diff-related-keys) set)
        new-gemset (->> parsed-new vals (map select-diff-related-keys) set)
        [old-gems new-gems & _] (data/diff old-gemset new-gemset)
        old-gems-by-name (select-keys parsed-old (map :name old-gems))
        new-gems-by-name (select-keys parsed-new (map :name new-gems))]
    (reduce
     (fn [acc name]
       (let [[old-gem] (old-gems-by-name name)
             [new-gem] (new-gems-by-name name)]
         (assoc acc name [(dissoc old-gem :name) (dissoc new-gem :name)])))
     {}
     (into #{} (into (keys old-gems-by-name) (keys new-gems-by-name))))))
