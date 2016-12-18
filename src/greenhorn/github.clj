(ns greenhorn.github
  (:require [greenhorn.gemfile-parsing :refer [diff-lock-files]]
            [greenhorn.background :as bg]
            [greenhorn.db :as db]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [tentacles.repos :as repos-api]))

(def ^:private token (System/getenv "GITHUB_TOKEN"))
(def ^:private user (System/getenv "GITHUB_USER"))
(def ^:private api-url (str "https://" user ":" token "@api.github.com/"))
(def ^:private html-url (str "https://github.com/"))
(def ^:private lockfile-path "Gemfile.lock")

(defn org-repos [org]
  (repos-api/org-repos org {:auth (str user ":" token) :all-pages true}))

(defn store-project-org-repos [id org]
  (let [repos-names (mapv #(% :name) (org-repos org))]
    (db/update-project id {:org_repos repos-names})))

(defn store-project-org-repos-async [id org]
  (bg/submit-job store-project-org-repos id org))

(defn- repo-content [repo path & params]
  (let [url (str api-url "repos/" repo "/contents/" path)]
    (:body (http/get url {:accept "application/vnd.github.v3.raw" :query-params (first params)}))))

(defn- repos-diff-lock-files
  [old-repo old-repo-params
   new-repo new-repo-params]
  (let [old-lock (repo-content old-repo lockfile-path old-repo-params)
        new-lock (repo-content new-repo lockfile-path new-repo-params)]
    (diff-lock-files old-lock new-lock)))

(defn- diff-lock-files-for-pull [pull]
  (let [{{old-ref :sha {old-repo :full_name} :repo} :base
         {new-ref :sha {new-repo :full_name} :repo} :head} pull]
    (repos-diff-lock-files old-repo {:ref old-ref} new-repo {:ref new-ref})))

(defn create-pull-comment [repo pull-num comment]
  (let [url (str api-url "repos/" repo "/issues/" pull-num "/comments")
        options {:throw-exceptions false :form-params {:body comment} :content-type :json}
        {status :status body :body} (http/post url options)]
    (when (= status 201)
      (json/parse-string body true))))

(defn update-pull-comment [repo comment-id comment]
  (let [url (str api-url "repos/" repo "/issues/comments/" comment-id)
        options {:throw-exceptions false :form-params {:body comment} :content-type :json}
        {status :status body :body} (http/patch url options)]
    (when (= status 200)
      (json/parse-string body true))))

(defn- gem-compare-ref [{:keys [version revision]}]
  (if version
    (->> version (take 8) (apply (partial str "v")))
    revision))

(defn- build-compare-url [gem-url old-gem new-gem]
  (str gem-url "/compare/" (gem-compare-ref old-gem) "..." (gem-compare-ref new-gem)))

(defn- comment-for-diff [gems-org gem-repo-present? [name [old-gem new-gem]]]
  (cond
    (and (nil? old-gem)
         (not (nil? new-gem))) (str "Gem `" name "` has been **added**")
    (and (nil? new-gem)
         (not (nil? old-gem))) (str "Gem `" name "` has been **deleted**")
    :else (let [gem-url (str html-url gems-org "/" name)
                updated-str (str "Gem `" name "` has been **updated**")]
            (if gem-repo-present?
              (str updated-str " " (build-compare-url gem-url old-gem new-gem))
              updated-str))))

(defn- gem-diffs->comment [gems-org org-repos diffs]
  (->> diffs
       (mapv
        (fn [[name _ :as diff]]
          (comment-for-diff gems-org (some #{name} org-repos) diff)))
       (str/join "\n")))

(defn- create-or-update-pull-comment
  [{project-id :id repo-path :full_name gems-org :gems_org org-repos :org_repos} pull-num diff]
  (let [{saved-comment-id :comment_id} (db/find-pull project-id pull-num)
        compare-urls (gem-diffs->comment gems-org org-repos diff)]
    (if saved-comment-id
      (let [{comment-id :id} (update-pull-comment repo-path saved-comment-id compare-urls)]
        (if comment-id
          (db/update-pull pull-num {:comment compare-urls})))
      (let [{comment-id :id} (create-pull-comment repo-path pull-num compare-urls)]
        (if comment-id
          (db/create-pull {:project_id project-id
                           :num pull-num
                           :comment_id comment-id
                           :comment compare-urls}))))))

(defn handle-pull-webhook [project {pull-num :number :as pull}]
  (let [diff (diff-lock-files-for-pull pull)]
    (if-not (empty? diff)
      (create-or-update-pull-comment project pull-num diff))))

(defn handle-pull-webhook-async [{action :action pull :pull_request}]
  (let [{{{full-name :full_name} :repo} :base} pull
        project (db/find-project-by {:full_name full-name})]
    (if (and project pull (contains? #{"opened" "reopened" "synchronize"} action))
      (bg/submit-job handle-pull-webhook project pull))))
