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
  (do
   (db/create-project {:name name :full_name full-name :gems_org gems-org})
   (redirect "/")))

(defn edit-project [id]
  (let [project (db/find-project-by {:id (Integer/parseInt id)})]
    (if project
      (views/edit-project project)
      (redirect "/"))))

(defn update-project [id {:keys [name full-name gems-org]}]
  (do
    (db/update-project id {:name name :full_name full-name :gems_org gems-org})
    (redirect "/")))

(defroutes app-routes
  (GET "/" [] (views/index (db/projects)))
  (GET "/projects/new" [] (views/add-project))
  (POST "/projects" {params :params} (create-project params))
  (GET "/projects/:id{[0-9]+}/edit" [id] (edit-project id))
  (POST "/projects/:id{[0-9]+}" {params :params} (update-project (params :id) params))
  (route/not-found "There is nothing here."))

(defroutes api-routes
  (POST "/github-webhook" {body :body} (do
                                         (github/handle-pull-webhook-async body)
                                         {:status 200})))

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
