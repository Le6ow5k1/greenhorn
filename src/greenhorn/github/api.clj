(ns greenhorn.github.api
  (:require [tentacles.repos :as repos-api]
            [environ.core :refer [env]]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [taoensso.timbre :as timbre]))

(def ^:private token (env :github-token))
(def ^:private user (env :github-user))
(def http-basic-auth-str (str user ":" token))
(def api-url (str "https://" http-basic-auth-str "@api.github.com/"))

(defn org-repos [org]
  (repos-api/org-repos org {:auth http-basic-auth-str :all-pages true}))

(defn compare-commit-messages
  "Fetches commit messages up to limit (10 by default) along with commit count from github compare API"
  [org repo base head & options]
  (let [{limit :limit :or {limit 10}} options
        response (repos-api/compare-commits org repo base head {:auth http-basic-auth-str})
        {:keys [commits total_commits] :or {messages [] total_commits 0}} response
        messages (->> commits
                      (take limit)
                      (mapv #(get-in % [:commit :message])))]
    {:messages messages :total_commits total_commits}))

(defn repo-content
  "Get's raw file in specified repo through github contents API"
  [repo path & params]
  (let [url (str api-url "repos/" repo "/contents/" path)]
    (:body (http/get url {:accept "application/vnd.github.v3.raw" :query-params (first params)}))))

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
