(ns greenhorn.github.comment-formatting
  (:require [clojure.string :as str]
            [greenhorn.github.api :as api]
            [taoensso.timbre :as timbre]))

(def html-url (str "https://github.com/"))

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

(defn- shorten-url [url]
  (format "[%s](%s)" (re-find #"[^\/]+$" url) url))

(defn- jira-urls [commit-body]
  (let [jira-urls (re-seq #"https?:\/{2}.*jira[\/\.\w-]+" commit-body)]
    (->> jira-urls (map shorten-url) (str/join ", "))))

(defn- escape-markdown-code [s]
  (str/replace s #"`" "``"))

(defn- author-avatar [avatar-url]
  (format "<img height=\"16\" src=\"%s?v=3&amp;s=32\" width=\"16\">" avatar-url))

(defn commit-to-markdown [{url :html_url {avatar-url :avatar_url} :author {:keys [message]} :commit}]
  (let [[header body] (str/split message #"\n" 2)
        escaped-header (escape-markdown-code header)
        avatar (author-avatar avatar-url)]
    (if body
      (let [jira-urls (jira-urls body)]
        (if (not-empty jira-urls)
          (format "  %s [`%s`](%s) | %s" avatar escaped-header url jira-urls)
          (format "  %s [`%s`](%s)" avatar escaped-header url)))
      (format "  %s [`%s`](%s)" avatar escaped-header url))))

(defn- add-over-limit-text-if-needed [over-limit-count commit-comments]
  (if (> over-limit-count 0)
    (conj commit-comments (str "  ... and " over-limit-count " more significant commit(s)"))
    commit-comments))

(defn commits-to-markdown [commits total]
  (let [over-limit-count (- total (count commits))]
    (->> commits
         (mapv commit-to-markdown)
         (add-over-limit-text-if-needed over-limit-count)
         (str/join "\n"))))

(defn- build-commit-messages-part [commits total status]
  (cond
    (= status "behind") "\n:arrow_down: this is a downgrade"
    (not-empty commits) (str "\n" (commits-to-markdown commits total))
    :else "\n:exclamation: no commits found for diff"))

(defn- gem-updated-comment [name gem-url old-gem new-gem]
  (let [gem-updated-part (format "**%s** has been updated" name)]
    (if gem-url
      (let [compare-url (compare-url gem-url old-gem new-gem)
            [_ org repo base head] (re-matches #"^.+\/([^\/]+)\/([^\/]+)\/compare\/(.+)\.{3}(.+)$" compare-url)
            {:keys [commits total status]} (api/compare-commits org repo base head)
            commit-messages-part (build-commit-messages-part commits total status)]
        (str gem-updated-part " "
             (shorten-url compare-url)
             commit-messages-part))
      (str gem-updated-part " " (compare-str old-gem new-gem)))))

(defn- gem-added-comment [name gem-url gem-spec]
  (if gem-url
    (let [gem-ref-url (str gem-url "/tree/" (gem-ref gem-spec))]
      (format "**%s** has been added %s" name (shorten-url gem-ref-url)))
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
