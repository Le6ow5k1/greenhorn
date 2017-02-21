(ns greenhorn.gemfile-parsing
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [clojure.data :as data]
            [taoensso.timbre :as timbre]))

(defprotocol Dependency
  (name [d])
  (version [d])
  (revision [d]))

(defrecord Gem [name version revision ref branch]
  Dependency
  (name [d] (d :name))
  (version [d] (d :version))
  (revision [d] (d :revision)))

(defprotocol DependencyDiff
  (name [d])
  (added? [d])
  (removed? [d]))

(defrecord GemDiff [name base-gem head-gem]
  DependencyDiff
  (name [d] (d :name))
  (added? [d] (and (nil? base-gem) head-gem))
  (removed? [d] (and base-gem (nil? head-gem))))

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
    (->> sections (map (comp (partial map map->Gem) parse-section)) (reduce concat))))

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
  [base-lock head-lock]
  (let [parsed-base (->> base-lock parse-lock-file (group-by :name))
        parsed-head (->> head-lock parse-lock-file (group-by :name))
        base-gemset (->> parsed-base vals (map select-diff-related-keys) set)
        head-gemset (->> parsed-head vals (map select-diff-related-keys) set)
        [base-gems head-gems & _] (data/diff base-gemset head-gemset)
        base-gems-by-name (select-keys parsed-base (map :name base-gems))
        head-gems-by-name (select-keys parsed-head (map :name head-gems))]
    (reduce
     (fn [acc name]
       (let [[base-gem] (base-gems-by-name name)
             [head-gem] (head-gems-by-name name)]
         (conj acc (->GemDiff name base-gem head-gem))))
     []
     (into #{} (into (keys base-gems-by-name) (keys head-gems-by-name))))))
