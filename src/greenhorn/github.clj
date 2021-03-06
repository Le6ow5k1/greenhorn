(ns greenhorn.github
  (:require [greenhorn.gemfile-parsing :as parsing]
            [greenhorn.background :as bg]
            [greenhorn.db :as db]
            [greenhorn.github.data-collecting :as data-collecting]
            [greenhorn.github.commenting :as commenting]
            [greenhorn.github.api :as api]
            [clj-http.client :as http]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]))

(def ^:private lockfile-name "Gemfile.lock")

(bg/define-worker store-project-org-repos-worker [id org]
  (let [repos-names (mapv #(% :name) (api/org-repos org))]
    (db/update-project id {:org_repos repos-names})))

(defn store-project-org-repos-async [id org]
  (bg/submit-job {} store-project-org-repos-worker id org))

(defn diff-lock-files-from-repos
  "Builds a diff for two Gemfile.lock files located in base-repo and head-repo"
  [base-repo base-ref head-repo head-ref]
  (let [base-lock (api/repo-content base-repo lockfile-name {:ref base-ref})
        head-lock (api/repo-content head-repo lockfile-name {:ref head-ref})]
    (parsing/diff-lock-files base-lock head-lock)))

(defn- create-or-update-pull-comment
  [{project-id :id repo-path :full_name gems-org :gems_org org-repos :org_repos} pull-num diff]
  (let [{saved-comment-id :comment_id} (db/find-pull project-id pull-num)
        gems-data (data-collecting/gems-data gems-org org-repos diff)
        body (commenting/gems-data-to-comment gems-data)]
    (if saved-comment-id
      (api/update-pull-comment repo-path saved-comment-id body)
      (let [{comment-id :id} (api/create-pull-comment repo-path pull-num body)]
        (if comment-id
          (db/create-pull {:project_id project-id
                           :num pull-num
                           :comment_id comment-id}))))))

(defn diff-lock-files-for-pull
  [{{base-ref :ref {base-repo :full_name} :repo} :base} merge-commit-sha]
  (diff-lock-files-from-repos base-repo base-ref base-repo merge-commit-sha))

(defn handle-ready-pull
  "Handles pull that's already has a merge commit"
  [{project-id :id :as project} {pull-num :number :as pull} merge-commit-sha]
  (let [diff (diff-lock-files-for-pull pull merge-commit-sha)]
    (when-not (empty? diff)
      (create-or-update-pull-comment project pull-num diff)
      (db/add-merge-commit-to-pull project-id pull-num merge-commit-sha))))

(defn- merge-commit-available?
  "If there is no merge commit or it's in the list of previous merge commits,it means that
  this merge commit is either not available or not updated yet and we can't trust it."
  [{previous-merge-commits :merge_commits} merge-commit-sha]
  (and merge-commit-sha
       (not (contains? (set previous-merge-commits) merge-commit-sha))))

(defn handle-pull
  ([project {merge-commit-sha :merge_commit_sha :as pull}]
   (when-not merge-commit-sha
     (throw (Exception. "merge_commit_sha isn't available on given pull")))
   (handle-ready-pull project pull merge-commit-sha))
  ([project {merge-commit-sha :merge_commit_sha :as pull} stored-pull]
   (when-not (merge-commit-available? stored-pull merge-commit-sha)
     (throw (Exception. "merge_commit_sha isn't available on given pull")))
   (handle-ready-pull project pull merge-commit-sha)))

(bg/define-worker handle-unready-pull-worker
  [{project-id :id :as project} repo-full-name pull-num]
  (let [[org repo] (str/split repo-full-name #"\/")
        pull (api/specific-pull org repo pull-num)
        stored-pull (db/find-pull project-id pull-num)]
    (if stored-pull
      (handle-pull project pull stored-pull)
      (handle-pull project pull))))

(defn- need-handle-webhook?
  [{:keys [action changes pull_request]}]
  (and pull_request
       (or
        (contains? #{"opened" "reopened" "synchronize"} action)
        (and (= action "edited") (contains? changes :base)))))

(defn handle-pull-webhook
  [{action :action pull :pull_request :as event-data}]
  (let [{pull-num :number merge-commit-sha :merge_commit_sha {{full-name :full_name} :repo} :base} pull
        {project-id :id :as project} (db/find-project-by {:full_name full-name})
        pull-persisted? (db/find-pull project-id pull-num)
        enqueue-delay (if pull-persisted? 3000 8000)]
    (if (and project (need-handle-webhook? event-data))
      ;; We are asking github API up to 3 times until merge commit for this pull-request becomes available.
      (bg/submit-job
       {:enqueue-delay enqueue-delay :retry-limit 2 :retry-delay 5000}
       handle-unready-pull-worker project full-name pull-num))))
