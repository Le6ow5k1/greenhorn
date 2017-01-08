(ns greenhorn.github
  (:require [greenhorn.gemfile-parsing :as parsing]
            [greenhorn.background :as bg]
            [greenhorn.db :as db]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [tentacles.repos :as repos-api]
            [environ.core :refer [env]]))

(def ^:private token (env :github-token))
(def ^:private user (env :github-user))
(def api-url (str "https://" user ":" token "@api.github.com/"))
(def html-url (str "https://github.com/"))
(def ^:private lockfile-path "Gemfile.lock")

(defn org-repos [org]
  (repos-api/org-repos org {:auth (str user ":" token) :all-pages true}))

(bg/define-worker store-project-org-repos-worker [id org]
  (let [repos-names (mapv #(% :name) (org-repos org))]
    (db/update-project id {:org_repos repos-names})))

(defn store-project-org-repos-async [id org]
  (bg/submit-job store-project-org-repos-worker id org))

(defn repo-content
  "Get's raw file in specified repo through github contents API"
  [repo path & params]
  (let [url (str api-url "repos/" repo "/contents/" path)]
    (:body (http/get url {:accept "application/vnd.github.v3.raw" :query-params (first params)}))))

(defn diff-lock-files-from-repos
  "Builds a diff for two Gemfile.lock files located in base-repo and head-repo"
  [base-repo base-ref head-repo head-ref]
  (let [base-lock (repo-content base-repo lockfile-path {:ref base-ref})
        head-lock (repo-content head-repo lockfile-path {:ref head-ref})]
    (parsing/diff-lock-files base-lock head-lock)))

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
  (if revision
    (->> revision (take 8) (apply str))
    (str "v" version)))

(defn- gem-compare-str [old-gem new-gem]
  (str (gem-compare-ref old-gem) "..." (gem-compare-ref new-gem)))

(defn- gem-url-from-remote
  "If git remote points to github we just replace git:// protocol with https://"
  [old-remote new-remote]
  (let [git-url-regex #"git(@|://)github.com"]
    (when (and
         (re-matches git-url-regex old-remote)
         (re-matches git-url-regex new-remote))
      (-> new-remote
          (str/replace git-url-regex html-url)
          (str/replace #".git$" "")))))

(defn- comment-for-diff
  [gems-org
   gem-repo-present?
   [name [{old-remote :remote :as old-gem} {new-remote :remote :as new-gem}]]]
  (cond
    (and (nil? old-gem)
         (not (nil? new-gem))) (str "Gem `" name "` has been **added**")
    (and (nil? new-gem)
         (not (nil? old-gem))) (str "Gem `" name "` has been **deleted**")
    :else (let [updated-str (str "Gem `" name "` has been **updated**")
                gem-url (or (gem-url-from-remote old-remote new-remote)
                            (when gem-repo-present? (str html-url gems-org "/" name)))]
            (if gem-url
              (str updated-str " " gem-url "/compare/" (gem-compare-str old-gem new-gem))
              (str updated-str " " (gem-compare-str old-gem new-gem))))))

(defn- gem-diffs->comment
  [gems-org org-repos diffs]
  (->> diffs
       (sort-by first)
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

(defn handle-pull
  "Function that deals with analyzing pull request
  and posting comment with list of dependency changes if needed"
  [project {pull-num :number :as pull}]
  (let [{{base-ref :sha {base-repo :full_name} :repo} :base
         {head-ref :sha {head-repo :full_name} :repo} :head} pull
        diff (diff-lock-files-from-repos base-repo base-ref head-repo head-ref)]
    (if-not (empty? diff)
      (create-or-update-pull-comment project pull-num diff))))

(bg/define-worker handle-pull-worker [project pull]
  (handle-pull project pull))

(defn handle-pull-webhook
  "Start asynchronous handling of a pull_request event"
  [{action :action pull :pull_request}]
  (let [{{{full-name :full_name} :repo} :base} pull
        project (db/find-project-by {:full_name full-name})]
    (if (and project pull (contains? #{"opened" "reopened" "synchronize"} action))
      (bg/submit-job handle-pull-worker project pull))))
