(defproject greenhorn "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.1"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-json "0.4.0"]
                 [clj-http "2.2.0"]
                 [hiccup "1.0.5"]
                 [cheshire "5.6.3"]
                 [org.clojure/java.jdbc "0.7.0-alpha1"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]
                 [ragtime "0.6.3"]
                 [org.clojure/core.async "0.2.395"]
                 [com.taoensso/timbre "4.7.4"]
                 [ring-logger-timbre "0.7.5"]
                 [tentacles "0.5.1"]
                 [clj-http-fake "1.0.2"]
                 [environ "1.1.0"]
                 [com.gfredericks/vcr-clj "0.4.8"]]
  :plugins [[lein-ring "0.9.7"]
            [lein-environ "1.1.0"]]
  :ring {:handler greenhorn.handler/app
         :init greenhorn.handler/init
         :destroy greenhorn.handler/stop}
  :aliases {"migrate"  ["run" "-m" "greenhorn.migrations/migrate"]
            "rollback" ["run" "-m" "greenhorn.migrations/rollback"]}
  :profiles
  {:dev {:dependencies []}})
