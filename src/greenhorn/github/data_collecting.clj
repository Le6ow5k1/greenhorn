(ns greenhorn.github.data-collecting
  (:require [greenhorn.github.api :as api]
            [clojure.string :as str]))

(def html-url "https://github.com/")

(defn- gem-ref [{:keys [version revision]}]
  (if revision
    (->> revision (take 7) (apply str))
    (when version
      (str "v" version))))

(defn- compare-str [old-gem new-gem]
  (let [old-ref (gem-ref old-gem)
        new-ref (gem-ref new-gem)]
    (when (and old-ref new-ref)
      (str old-ref "..." new-ref))))

(defn- compare-url [gem-url compare-str]
  (when (not (empty? compare-str))
    (str gem-url "/compare/" compare-str)))

(defn- gem-status
  [old-gem new-gem]
  (cond
    (and (nil? old-gem) (not (nil? new-gem))) :added
    (and (nil? new-gem) (not (nil? old-gem))) :deleted
    :else :updated))

(defn- diff-commits-for-gem
  [compare-url old-gem new-gem]
  (if compare-url
    (let [[_ org repo base head] (re-matches #"^.+\/([^\/]+)\/([^\/]+)\/compare\/(.+)\.{3}(.+)$" compare-url)]
      (api/compare-commits org repo base head))
    {:commits [] :behind-by nil}))

(defn- collect-gem-data
  "Returns map with information about gem update which then being used for creating comments.
  Note that this function queries github API in order to get diff commits."
  [[name [old-gem new-gem] :as diff]
   {:keys [url] :or {url nil} :as additional-data}]
  (let [old-gem-ref (gem-ref old-gem)
        new-gem-ref (gem-ref new-gem)
        status (gem-status old-gem new-gem)
        compare-str (compare-str old-gem new-gem)
        compare-url (and url (compare-url url compare-str))
        {:keys [commits behind-by]} (diff-commits-for-gem compare-url old-gem new-gem)
        downgraded? (and behind-by (>= behind-by 1))
        data {:name name
              :old-gem old-gem
              :new-gem new-gem
              :old-gem-ref old-gem-ref
              :new-gem-ref new-gem-ref
              :status status
              :compare-str compare-str
              :compare-url compare-url
              :diff-commits commits
              :diff-behind-by behind-by
              :downgraded? downgraded?}]
    (merge data additional-data)))

(defn- gem-name-and-remote-matches? [name remote]
  (let [[_ name-from-remote] (re-matches #".*\/([^\/]+).git$" remote)]
    (= name-from-remote name)))

(defn- gem-url-from-remote
  [name old-remote new-remote]
  (let [git-url-regex #"git(@|://)github.com.*"]
    (when (and old-remote
               new-remote
               (re-matches git-url-regex old-remote)
               (re-matches git-url-regex new-remote)
               (gem-name-and-remote-matches? name old-remote)
               (gem-name-and-remote-matches? name new-remote))
      (-> new-remote
          (str/replace #"git(@|://)github.com(/|:)" html-url)
          (str/replace #".git$" "")))))

(defn- gem-url
  [gems-org
   is-gem-repo-in-org?
   [name [{old-remote :remote} {new-remote :remote}]]]
  (or (gem-url-from-remote name old-remote new-remote)
      (when is-gem-repo-in-org? (str html-url gems-org "/" name))))

(defn gems-data
  "Returns a vector of maps containing needed information for describing gem update."
  [gems-org org-repos diffs]
  (mapv (fn [[name _ :as diff]]
          (let [is-gem-repo-in-org? (some #{name} org-repos)
                url (gem-url gems-org is-gem-repo-in-org? diff)]
            (collect-gem-data diff {:url url})))
        diffs))
