(ns greenhorn.github.comment-formatting
  (:require [clojure.string :as str]))

(def html-url (str "https://github.com/"))

(defn- gem-ref [{:keys [version revision]}]
  (if revision
    (->> revision (take 7) (apply str))
    (str "v" version)))

(defn- gem-name-and-remote-matches? [name remote]
  (let [[_ name-from-remote] (re-matches #".*\/([^\/]+).git$" remote)]
    (= name-from-remote name)))

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

(defn- shorten-url-md [url]
  (let [url-desc (re-find #"[^\/]+$" url)]
    (str "[" url-desc "]" "(" url ")")))

(defn- gem-compare-str [old-gem new-gem]
  (str (gem-ref old-gem) "..." (gem-ref new-gem)))

(defn- gem-updated-str-md [name gem-url old-gem new-gem]
  (let [updated-str (str "**" name "** has been updated")
        compare-url (when gem-url (shorten-url-md (str gem-url "/compare/" (gem-compare-str old-gem new-gem))))]
    (if compare-url
      (str updated-str " " compare-url)
      (str updated-str " " (gem-compare-str old-gem new-gem)))))

(defn- gem-added-str-md [name gem-url gem-spec]
  (if gem-url
    (let [gem-ref-url (str gem-url "/tree/" (gem-ref gem-spec))]
      (str "**" name "** has been added " (shorten-url-md gem-ref-url)))
    (str "**" name "** has been added")))

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
           (not (nil? old-gem))) (str "**" name "** has been deleted")
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
