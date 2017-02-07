(ns greenhorn.db
  (:require [environ.core :refer [env]]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str])
  (:import org.postgresql.jdbc4.Jdbc4Array
           clojure.lang.IPersistentVector))

(def ^:const db-uri (env :database-url))

(extend-protocol jdbc/IResultSetReadColumn
  Jdbc4Array
  (result-set-read-column [v _ _] (vec (.getArray v))))

(extend-protocol jdbc/ISQLValue
  IPersistentVector
  (sql-value [v]
    (let [conn (jdbc/get-connection db-uri)]
      (.createArrayOf conn "varchar" (into-array String v)))))

(defn create-pull [attrs]
  (jdbc/insert! db-uri :pulls attrs))

(defn update-pull [project-id num attrs]
  (jdbc/update! db-uri :pulls attrs ["project_id = ? and num = ?" project-id num]))

(defn find-pull [project-id num]
  (let [result (jdbc/find-by-keys db-uri :pulls {:project_id project-id :num num})]
    (first result)))

(defn pulls []
  (jdbc/query db-uri ["select * from pulls"]))

(defn projects
  ([] (jdbc/query db-uri ["select * from projects"]))
  ([attrs] (jdbc/find-by-keys db-uri :projects attrs)))

(defn find-project-by [attrs]
  (first (projects attrs)))

(defn create-project [attrs]
  (jdbc/insert! db-uri :projects attrs))

(defn update-project [id attrs]
  (let [parsed-id (if (integer? id) id (Integer/parseInt id))]
    (jdbc/update! db-uri :projects attrs ["id = ?", parsed-id])))
