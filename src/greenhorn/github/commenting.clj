(ns greenhorn.github.commenting
  (:require [clojure.string :as str]
            [environ.core :refer [env]]))

(def anonymous-user-avatar-url "https://i2.wp.com/assets-cdn.github.com/images/gravatars/gravatar-user-420.png")
(def visible-commits-limit 10)
(def link-extraction-re (re-pattern (get env :link-extraction-re "https?:\\/{2}[\\w-]*[\\/\\.\\S]+")))

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

(defn- gem-updated-comment
  [{:keys [name
           old-gem
           new-gem
           compare-str
           compare-url
           diff-commits
           behind-by]}]
  (let [gem-updated-part (format "**%s** has been updated" name)]
    (if (not (empty? diff-commits))
      (let [commit-messages-part (build-commit-messages-part diff-commits behind-by)]
        (str gem-updated-part " " (shorten-link compare-url) commit-messages-part))
      (str gem-updated-part " " compare-str))))

(defn- gem-added-comment
  [{:keys [name url new-gem-ref]}]
  (let [ref-url (and url (str url "/tree/" new-gem-ref))]
    (if ref-url
      (format "**%s** has been added %s" name (shorten-link ref-url))
      (format "**%s** has been added %s" name))))

(defn gem-data-to-comment
  [{:keys [name status] :as data}]
  (case status
    :added (gem-added-comment data)
    :deleted (format "**%s** has been deleted" name)
    :updated (gem-updated-comment data)))

(defn gems-data-to-comment
  [gems-data]
  (->> gems-data
       (sort-by :name)
       (mapv
        (fn [data]
          (str "- " (gem-data-to-comment data))))
       (str/join "\n")))
