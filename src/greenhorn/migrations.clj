(ns greenhorn.migrations
  (:require [ragtime.jdbc :as jdbc]
            [ragtime.repl :as repl]
            [greenhorn.db :as db]))

(defn load-config []
  {:datastore  (jdbc/sql-database db/db-uri)
   :migrations (jdbc/load-resources "migrations")})

(defn migrate []
  (repl/migrate (load-config)))

(defn rollback []
  (repl/rollback (load-config)))
