(ns greenhorn.db
  (:require [clojure.java.jdbc :as jdbc])
  (:require [clojure.string :as str]))

(def ^:const db-uri (System/getenv "PG_URI"))

(defn- key-underscore [key]
  (-> key
      str
      (str/replace #"/-+" "_")
      keyword))

(defn- underscore-keys [attrs]
  (reduce-kv
   (fn [m k v] (assoc m (key-underscore k) v) )
   {}
   attrs))

(defn create-pull [attrs]
  (jdbc/insert! db-uri :pulls attrs))

(defn update-pull [num attrs]
  (jdbc/update! db-uri :pulls attrs ["num = ?", num]))

(defn find-pull [project-id num]
  (let [result (jdbc/find-by-keys db-uri :pulls {:project_id project-id :num num})]
    (first result)))

(defn pulls []
  (jdbc/query db-uri ["select * from pulls"]))

(defn projects []
  (jdbc/query db-uri ["select * from projects"]))

(defn find-project-by [attrs]
  (let [result (jdbc/find-by-keys db-uri :projects attrs)]
    (first result)))

(defn create-project [attrs]
  (jdbc/insert! db-uri :projects attrs))

(defn update-project [id attrs]
  (jdbc/update! db-uri :projects attrs ["id = ?", (Integer/parseInt id)]))
