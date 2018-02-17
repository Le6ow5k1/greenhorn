(ns greenhorn.github.comment-formatting
  (:require [clojure.string :as str]
            [greenhorn.github.api :as api]
            [environ.core :refer [env]]
            [taoensso.timbre :as timbre]))

(def html-url (str "https://github.com/"))
(def anonymous-user-avatar-url "https://i2.wp.com/assets-cdn.github.com/images/gravatars/gravatar-user-420.png")
(def visible-commits-limit 10)
(def link-extraction-re (re-pattern (get env :link-extraction-re "https?:\\/{2}[\\w-]*[\\/\\.\\S]+")))

(defn- gem-name-and-remote-matches? [name remote]
  (let [[_ name-from-remote] (re-matches #".*\/([^\/]+).git$" remote)]
    (= name-from-remote name)))

(defn- gem-ref [{:keys [version revision]}]
  (if revision
    (->> revision (take 7) (apply str))
    (str "v" version)))

(defn- compare-str [old-gem new-gem]
  (str (gem-ref old-gem) "..." (gem-ref new-gem)))

(defn- compare-url [gem-url old-gem new-gem]
  (str gem-url "/compare/" (compare-str old-gem new-gem)))

(defn- shorten-link [url]
  (format "[%s](%s)" (re-find #"[^\/\]\[\s]+$" url) url))

(defn- extract-links [commit-body]
  (let [links (re-seq link-extraction-re commit-body)]
    (->> links (map shorten-link) (str/join ", "))))

(defn- author-avatar [avatar-url]
  (let [url (or avatar-url anonymous-user-avatar-url)]
    (format "<img height=\"16\" src=\"%s?v=3&amp;s=32\" width=\"16\">" url)))

(defn commit-to-markdown [{url :html_url {avatar-url :avatar_url} :author {:keys [message]} :commit}]
  (let [[header body] (str/split message #"\n" 2)
        avatar (author-avatar avatar-url)]
    (if body
      (let [links (extract-links body)]
        (if (not-empty links)
          (format "  %s [`` %s ``](%s) • %s" avatar header url links)
          (format "  %s [`` %s ``](%s)" avatar header url)))
      (format "  %s [`` %s ``](%s)" avatar header url))))

(defn commits-to-markdown [commits]
  (let [over-limit-count (- (count commits) visible-commits-limit)
        commits-part (->> commits
                          (take visible-commits-limit)
                          (mapv commit-to-markdown)
                          (str/join "\n"))]
    (if (<= over-limit-count 0)
      commits-part
      (str commits-part
           "\n"
           "  ... and " over-limit-count " more significant commit(s)"))))

(defn- build-commit-messages-part [commits behind-by]
  (cond
    (and behind-by (= behind-by 1)) (format "\n:open_mouth: %s commit is missing" behind-by)
    (and behind-by (> behind-by 1)) (format "\n:open_mouth: %s commits are missing" behind-by)
    (not-empty commits) (str "\n" (commits-to-markdown commits))
    :else "\n:confused: no commits found for diff"))

(defn- gem-updated-comment [name gem-url old-gem new-gem]
  (let [gem-updated-part (format "**%s** has been updated" name)]
    (if gem-url
      (let [compare-url (compare-url gem-url old-gem new-gem)
            [_ org repo base head] (re-matches #"^.+\/([^\/]+)\/([^\/]+)\/compare\/(.+)\.{3}(.+)$" compare-url)
            {:keys [commits behind-by]} (api/compare-commits org repo base head)
            commit-messages-part (build-commit-messages-part commits behind-by)]
        (str gem-updated-part " "
             (shorten-link compare-url)
             commit-messages-part))
      (str gem-updated-part " " (compare-str old-gem new-gem)))))

(defn- gem-added-comment [name gem-url gem-spec]
  (if gem-url
    (let [gem-ref-url (str gem-url "/tree/" (gem-ref gem-spec))]
      (format "**%s** has been added %s" name (shorten-link gem-ref-url)))
    (format "**%s** has been added %s" name (gem-ref gem-spec))))

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

(defn diff-to-comment
  [gems-org
   gem-repo-present?
   [name [{old-remote :remote :as old-gem} {new-remote :remote :as new-gem}]]]
  (let [gem-url (or (gem-url-from-remote name old-remote new-remote)
                    (when gem-repo-present? (str html-url gems-org "/" name)))]
    (cond
      (and (nil? old-gem)
           (not (nil? new-gem))) (gem-added-comment name gem-url new-gem)
      (and (nil? new-gem)
           (not (nil? old-gem))) (format "**%s** has been deleted" name)
      :else (gem-updated-comment name gem-url old-gem new-gem))))

(defn diffs-to-comment
  [gems-org org-repos diffs]
  (->> diffs
       (sort-by first)
       (mapv
        (fn [[name _ :as diff]]
          (str "- " (diff-to-comment gems-org (some #{name} org-repos) diff))))
       (str/join "\n")))
