(ns greenhorn.github.api
  (:require [tentacles.repos :as repos-api]
            [tentacles.pulls :as pulls-api]
            [environ.core :refer [env]]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [taoensso.timbre :as timbre]
            [cemerick.url :refer [url] :rename {url parse-url}]))

(def ^:private token (env :github-token))
(def ^:private user (env :github-user))
(def http-basic-auth-str (str user ":" token))
(def api-url (str "https://api.github.com/"))

(defn specific-pull [org repo id]
  (pulls-api/specific-pull org repo id {:auth http-basic-auth-str}))

(defn org-repos [org]
  (repos-api/org-repos org {:auth http-basic-auth-str :all-pages true}))

(defn- merge-commit? [{parents :parents}]
  (>= (count parents) 2))

(defn compare-commits
  [org repo base head]
  (let [response (repos-api/compare-commits org repo base head {:auth http-basic-auth-str})
        {:keys [commits status behind_by] :or {commits []}} response
        commits (->> commits
                     reverse
                     (remove merge-commit?))]
    {:commits commits :status status :behind-by behind_by}))

(defn get-file [url & options]
  (let [default-options {:accept "application/vnd.github.v3.raw" :basic-auth http-basic-auth-str}
        coerced-options (merge (or (first options) {}) default-options)
        {status :status body :body} (http/get url coerced-options)]
    (if (= status 200)
      body
      nil)))

(defn repo-content
  "Get's raw file in specified repo through github contents API"
  [repo path & params]
  (let [url (str api-url "repos/" repo "/contents/" path)]
    (get-file url {:query-params (first params)})))

(defn create-pull-comment [repo pull-num comment]
  (let [url (str api-url "repos/" repo "/issues/" pull-num "/comments")
        options {:form-params {:body comment} :content-type :json :basic-auth http-basic-auth-str}
        {status :status body :body} (http/post url options)]
    (when (= status 201)
      (json/parse-string body true))))

(defn update-pull-comment [repo comment-id comment]
  (let [url (str api-url "repos/" repo "/issues/comments/" comment-id)
        options {:form-params {:body comment} :content-type :json :basic-auth http-basic-auth-str}
        {status :status body :body} (http/patch url options)]
    (when (= status 200)
      (json/parse-string body true))))
