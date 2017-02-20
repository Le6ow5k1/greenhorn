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

(defn commit-to-markdown [{:keys [url message]}]
  (let [[header body] (str/split message #"\n\n" 2)]
    (if body
      (let [jira-urls (jira-urls body)]
        (if (not-empty jira-urls)
          (format "  - [`%s`](%s) | %s" header url jira-urls)
          (format "  - [`%s`](%s)" header url)))
      (format "  - [`%s`](%s)" header url))))

(defn commits-to-markdown [commits total]
  (let [over-limit (- total (count commits))]
    (->> commits
         (mapv commit-to-markdown)
         ((fn [m]
            (or (and (> over-limit 0) (conj m (str "  - ... and " over-limit " more significant commit(s)")))
                m)))
         (str/join "\n"))))

(defn- gem-updated-str-md [name gem-url old-gem new-gem]
  (let [updated-str (format "**%s** has been updated" name)]
    (if gem-url
      (let [compare-url (compare-url gem-url old-gem new-gem)
            [_ org repo base head] (re-matches #"^.+\/([^\/]+)\/([^\/]+)\/compare\/(.+)\.{3}(.+)$" compare-url)
            {:keys [commits total]} (api/compare-commits org repo base head)
            formatted-messages (commits-to-markdown commits total)]
        (str updated-str " "
             (shorten-url compare-url)
             (when-not (empty? formatted-messages) (str "\n" formatted-messages))))
      (str updated-str " " (compare-str old-gem new-gem)))))

(defn- gem-added-str-md [name gem-url gem-spec]
  (if gem-url
    (let [gem-ref-url (str gem-url "/tree/" (gem-ref gem-spec))]
      (format "**%s** has been added %s" name (shorten-url gem-ref-url)))
    (format "**%s** has been added %s" name (gem-ref gem-spec))))

(defn- gem-url-from-remote
  "Trying to infer gem url from remote"
  [name old-remote new-remote]
  (let [git-url-regex #"git(@|://)github.com.*"]
    ;; If git remote points to github we just replace git:// protocol with https://
    (when (and old-remote
               new-remote
               (re-matches git-url-regex old-remote)
               (re-matches git-url-regex new-remote)
               (gem-name-and-remote-matches? name old-remote)
               (gem-name-and-remote-matches? name new-remote))
      (-> new-remote
          (str/replace #"git(@|://)github.com/" html-url)
          (str/replace #".git$" "")))))

(defn diff-to-markdown
  [gems-org
   gem-repo-present?
   [name [{old-remote :remote :as old-gem} {new-remote :remote :as new-gem}]]]
  (let [gem-url (or (gem-url-from-remote name old-remote new-remote)
                    (when gem-repo-present? (str html-url gems-org "/" name)))]
    (cond
      (and (nil? old-gem)
           (not (nil? new-gem))) (gem-added-str-md name gem-url new-gem)
      (and (nil? new-gem)
           (not (nil? old-gem))) (format "**%s** has been deleted" name)
      :else (gem-updated-str-md name gem-url old-gem new-gem))))

(defn diffs-to-markdown
  "Turns sequence of gem diffs into markdown string"
  [gems-org org-repos diffs]
  (->> diffs
       (sort-by first)
       (mapv
        (fn [[name _ :as diff]]
          (str "- " (diff-to-markdown gems-org (some #{name} org-repos) diff))))
       (str/join "\n")))
