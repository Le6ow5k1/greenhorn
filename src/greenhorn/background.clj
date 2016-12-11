(ns greenhorn.background
  (:require [clojure.core.async :refer :all :as async]
            [taoensso.timbre :as timbre]))

(def ^:const job-timeout 30000)
(def ^:private workers (atom {}))
(def ^:private queue (chan (sliding-buffer 10)))
(def ^:private job-ch (chan 1))

(defn submit-job [worker-fn & args]
  (let [worker-id (str (java.util.UUID/randomUUID))]
    (swap! workers assoc worker-id worker-fn)
    (>!! queue {:worker-id worker-id :args args})
    (timbre/info (str "queued job " worker-id " with args: " args))))

(defn- execute-job [ch worker-id args]
  (go
    (let [worker (@workers worker-id)]
      (if worker
        (try
          (timbre/info (str "start executing job " worker-id))
          (apply worker args)
          (timbre/info (str "done executing job " worker-id))
          (catch Throwable e
            (timbre/error (str "failure executing job " worker-id " with args: " args))
            (timbre/error e))
          (finally (>!! ch :done)))
        (>!! ch :done)))))

(defn start! []
  (go-loop []
    (let [{:keys [worker-id args]} (<! queue)
          w (execute-job job-ch worker-id args)
          [status _] (alts!! [job-ch (timeout job-timeout)])]
      (case status
        :stop (timbre/info (str "stoping workers"))
        :done (do
                (timbre/info (str "finish job " worker-id))
                (recur))
        (do
          (timbre/warn (str "time out reached by job " worker-id))
          (close! w)
          (close! job-ch)
          (recur))))))

(defn stop! []
  (>!! job-ch :stop))
