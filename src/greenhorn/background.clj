(ns greenhorn.background
  (:require [clojure.core.async :refer :all :as async]
            [taoensso.timbre :as timbre]))

(def default-config {:job-timeout 30000
                     :max-threads 20
                     :queue-size 100})

(def workers (atom {}))
(def queue (chan (dropping-buffer (default-config :queue-size))))
(def ^:private executor-ch (chan))

(defmacro define-worker
  "Defines a function that will do the work.

      (define-worker send-email recipient body
        ...
        )"
  [& function-definition]
  (let [symbol-name (first function-definition)
        str-ns (str *ns* "/" symbol-name)]
    `(do
       (defn ~@function-definition)
       (swap! workers assoc
              ~str-ns ~symbol-name
              ~symbol-name ~str-ns))))

(defn submit-job
  "Places a job (worker and it's args) into the queue for future processing.

      (submit-job send-email \"foo@example.com\" \"Hello, world!\")"
  [worker & args]
  (let [worker-name (@workers worker)]
    (timbre/info (str "Queueing job " worker-name " with args: " args))
    (>!! queue {:worker-name worker-name :args args})))

(defn- execute-job
  [buffer-chan timeout-ms]
  (loop []
    (let [[{:keys [worker-name args]} _] (alts!! [buffer-chan (timeout timeout-ms)])
          worker (@workers worker-name)]
      (if worker
        (try
          (timbre/info (str "Start executing job " worker-name))
          (apply worker args)
          (timbre/info (str "Done executing job " worker-name))
          (catch Throwable e
            (timbre/error (str "Failure executing job " worker-name " with args: " args))
            (timbre/error e)))
        (recur)))))

(defn- thread-pool-service
  [queue-chan max-threads timeout-ms]
  (let [thread-count (atom 0)
        buffer-chan (chan)]
    (go (loop []
          (when-let [[job _] (alts! [queue-chan executor-ch])]
            (if (= job :stop)
              (do
                (timbre/info "Stopping thread pool")
                (close! buffer-chan))
              (if-not (alt! [[buffer-chan job]] true
                            :default false)
                (loop []
                  (if (< @thread-count max-threads)
                    (do (put! buffer-chan job)
                        (thread
                          (swap! thread-count inc)
                          (execute-job buffer-chan timeout-ms)
                          (swap! thread-count dec)))
                    (when-not (alt! [[buffer-chan job]] true
                                    [(timeout 1000)] false)
                      (recur)))))))
            (recur))
        (close! buffer-chan))))

(defn start! []
  (thread-pool-service queue (default-config :max-threads) (default-config :job-timeout)))

(defn stop! []
  (>!! executor-ch :stop))
