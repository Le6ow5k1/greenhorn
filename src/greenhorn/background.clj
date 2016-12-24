(ns greenhorn.background
  (:require [clojure.core.async :refer :all :as async]
            [taoensso.timbre :as timbre]))

(def ^:const job-timeout 30000)
(def workers (atom {}))
(def ^:private queue (chan (sliding-buffer 10)))
(def ^:private job-ch (chan 1))

(defmacro define-worker [& function-definition]
  (let [symbol-name (first function-definition)
        str-ns (str *ns* "/" symbol-name)]
    `(do
       (defn ~@function-definition)
       (swap! workers assoc
              ~str-ns ~symbol-name
              ~symbol-name ~str-ns))))

(defn submit-job [worker & args]
  (let [worker-name (@workers worker)]
    (>!! queue {:worker-name worker-name :args args})
    (timbre/info (str "queued job " worker-name " with args: " args))))

(defn- execute-job [ch worker-name args]
  (go
    (let [worker (@workers worker-name)]
      (if worker
        (try
          (timbre/info (str "start executing job " worker-name))
          (apply worker args)
          (timbre/info (str "done executing job " worker-name))
          (catch Throwable e
            (timbre/error (str "failure executing job " worker-name " with args: " args))
            (timbre/error e))
          (finally (>!! ch :done)))
        (>!! ch :done)))))

(defn start! []
  (go-loop []
    (let [{:keys [worker-name args]} (<! queue)
          w (execute-job job-ch worker-name args)
          [status _] (alts!! [job-ch (timeout job-timeout)])]
      (case status
        :stop (timbre/info (str "stoping workers"))
        :done (do
                (timbre/info (str "finish job " worker-name))
                (recur))
        (do
          (timbre/warn (str "time out reached by job " worker-name))
          (close! w)
          (close! job-ch)
          (recur))))))

(defn stop! []
  (>!! job-ch :stop))
