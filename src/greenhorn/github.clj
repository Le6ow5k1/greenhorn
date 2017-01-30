(ns greenhorn.github
  (:require [greenhorn.gemfile-parsing :as parsing]
            [greenhorn.background :as bg]
            [greenhorn.db :as db]
            [greenhorn.github.comment-formatting :refer [diffs-to-markdown]]
            [greenhorn.github.api :as api]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]))

(def ^:private lockfile-path "Gemfile.lock")

(bg/define-worker store-project-org-repos-worker [id org]
  (let [repos-names (mapv #(% :name) (api/org-repos org))]
    (db/update-project id {:org_repos repos-names})))

(defn store-project-org-repos-async [id org]
  (bg/submit-job store-project-org-repos-worker id org))

(defn diff-lock-files-from-repos
  "Builds a diff for two Gemfile.lock files located in base-repo and head-repo"
  [base-repo base-ref head-repo head-ref]
  (let [base-lock (api/repo-content base-repo lockfile-path {:ref base-ref})
        head-lock (api/repo-content head-repo lockfile-path {:ref head-ref})]
    (parsing/diff-lock-files base-lock head-lock)))

(defn- create-or-update-pull-comment
  [{project-id :id repo-path :full_name gems-org :gems_org org-repos :org_repos} pull-num diff]
  (let [{saved-comment-id :comment_id} (db/find-pull project-id pull-num)
        body (diffs-to-markdown gems-org org-repos diff)]
    (if saved-comment-id
      (api/update-pull-comment repo-path saved-comment-id body)
      (let [{comment-id :id} (api/create-pull-comment repo-path pull-num body)]
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
