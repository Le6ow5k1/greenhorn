(ns greenhorn.github
  (:require [greenhorn.gemfile-parsing :as parsing]
            [greenhorn.background :as bg]
            [greenhorn.db :as db]
            [greenhorn.github.comment-formatting :refer [diffs-to-markdown]]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [tentacles.repos :as repos-api]
            [environ.core :refer [env]]))

(def ^:private token (env :github-token))
(def ^:private user (env :github-user))
(def http-basic-auth-str (str user ":" token))
(def api-url (str "https://" http-basic-auth-str "@api.github.com/"))
(def ^:private lockfile-path "Gemfile.lock")

(defn org-repos [org]
  (repos-api/org-repos org {:auth http-basic-auth-str :all-pages true}))

(bg/define-worker store-project-org-repos-worker [id org]
  (let [repos-names (mapv #(% :name) (org-repos org))]
    (db/update-project id {:org_repos repos-names})))

(defn store-project-org-repos-async [id org]
  (bg/submit-job store-project-org-repos-worker id org))

(defn compare-commit-messages
  "Fetches commit messages along with commit count from github compare API"
  [org repo base head & options]
  (let [{limit :limit :or {limit 10}} options
        response (repos-api/compare-commits org repo base head {:auth http-basic-auth-str})
        {:keys [commits total_commits]} response
        messages (->> commits
                      (take limit)
                      (mapv #(get-in % [:commit :message])))]
    {:messages messages :total_commits total_commits}))

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
        options {:form-params {:body comment} :content-type :json}
        {status :status body :body} (http/post url options)]
    (when (= status 201)
      (json/parse-string body true))))

(defn update-pull-comment [repo comment-id comment]
  (let [url (str api-url "repos/" repo "/issues/comments/" comment-id)
        options {:form-params {:body comment} :content-type :json}
        {status :status body :body} (http/patch url options)]
    (when (= status 200)
      (json/parse-string body true))))

(defn- create-or-update-pull-comment
  [{project-id :id repo-path :full_name gems-org :gems_org org-repos :org_repos} pull-num diff]
  (let [{saved-comment-id :comment_id} (db/find-pull project-id pull-num)
        body (diffs-to-markdown gems-org org-repos diff)]
    (if saved-comment-id
      (update-pull-comment repo-path saved-comment-id body)
      (let [{comment-id :id} (create-pull-comment repo-path pull-num body)]
        (if comment-id
          (db/create-pull {:project_id project-id
                           :num pull-num
                           :comment_id comment-id}))))))

(defn handle-pull
  "Function that deals with analyzing pull request
  and posting comment with list of dependency changes if needed"
  [project {pull-num :number :as pull}]
  (let [{{base-ref :ref {base-repo :full_name} :repo} :base
         {head-ref :ref {head-repo :full_name} :repo} :head} pull
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
