(ns greenhorn.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-body]]
            [ring.util.response :refer [response redirect]]
            [greenhorn.views :as views]
            [greenhorn.github :as github]
            [greenhorn.background :as bg]
            [greenhorn.db :as db]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as timbre.appenders]
            [ring.logger.timbre :as logger.timbre]))

(defn index []
  (views/index (db/projects)))

(defn create-project [{:keys [name full-name gems-org]}]
  (let [[{id :id}] (db/create-project {:name name :full_name full-name :gems_org gems-org})]
    (if id
      (do (github/store-project-org-repos-async id gems-org)
          (redirect "/"))
      (views/add-project))))

(defn edit-project [id]
  (let [project (db/find-project-by {:id (Integer/parseInt id)})]
    (if project
      (views/edit-project project)
      (redirect "/"))))

(defn update-project [id {:keys [name full-name gems-org]}]
  (let [project {:name name :full_name full-name :gems_org gems-org}
        [updated?] (db/update-project id project)]
    (if updated?
      (do (github/store-project-org-repos-async id gems-org)
          (redirect "/"))
      (views/edit-project (assoc project :id id)))))

(defn github-webhook [event-name body]
  (if (= event-name "pull_request")
    (do
      (github/handle-pull-webhook event-name body)
      {:status 200})
    {:status 404}))

(defroutes app-routes
  (GET "/" [] (views/index (db/projects)))
  (GET "/projects/new" [] (views/add-project))
  (POST "/projects" {params :params} (create-project params))
  (GET "/projects/:id{[0-9]+}/edit" [id] (edit-project id))
  (POST "/projects/:id{[0-9]+}" {params :params} (update-project (params :id) params))
  (route/not-found "There is nothing here."))

(defroutes api-routes
  (POST "/github-webhook" {body :body {event-name "X-GitHub-Event"} :headers} (github-webhook event-name body)))

(def app
  (routes
   (-> api-routes
       (wrap-routes wrap-json-body {:keywords? true})
       (logger.timbre/wrap-with-logger))
   (-> app-routes
       (wrap-defaults site-defaults)
       (logger.timbre/wrap-with-logger))))

(defn init []
  (timbre/merge-config!
   {:appenders {:server (assoc (timbre.appenders/spit-appender {:fname "server.log"})
                               :ns-whitelist ["ring.logger.timbre"])
                :bg (assoc (timbre.appenders/spit-appender {:fname "bg.log"})
                           :ns-whitelist ["greenhorn.background"])}})
  (bg/start!))

(defn stop []
  (bg/stop!))
