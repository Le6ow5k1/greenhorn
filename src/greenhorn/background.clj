(ns greenhorn.background
  (:require [clojure.core.async :as async]
            [taoensso.timbre :as timbre]))

(def default-config {:job-timeout 30000
                     :max-threads 20
                     :queue-size 100
                     :retry-delay 1000
                     :retry-limit 3})

(def workers (atom {}))
(def queue (async/chan (async/dropping-buffer (default-config :queue-size))))
(def default-retry-opts (select-keys default-config [:retry-delay :retry-limit]))
(def ^:private executor-ch (async/chan))

(defmacro define-worker
  "Defines a function that will do the work.

      (define-worker send-email [recipient body]
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

(defn- enqueue [job-info]
  (async/>!! queue job-info)
  (timbre/info (str "Enqueued job: " job-info)))

(defn submit-job
  "Places a job (worker and it's args) into the queue for future processing.

      (submit-job {:retry-limit 4 :retry-delay 3000} send-email \"foo@example.com\" \"Hello, world!\")"
  [opts worker & args]
  (let [given-opts (select-keys opts [:enqueue-delay :retry-delay :retry-limit])
        {:keys [enqueue-delay] :as coerced-opts} (merge default-retry-opts given-opts)
        job-info {:worker-name (@workers worker) :args args :opts coerced-opts :retries 0}]
    (if enqueue-delay
      (async/go
        (async/<! (async/timeout enqueue-delay))
        (enqueue job-info))
      (enqueue job-info))))

(defn- execute-job
  [buffer-chan timeout-ms]
  (loop []
    (let [[{:keys [worker-name args opts retries] :as job-info} _] (async/alts!! [buffer-chan (async/timeout timeout-ms)])
          {:keys [retry-delay retry-limit]} opts
          need-retry? (and retry-limit (> retry-limit retries))
          worker (@workers worker-name)]
      (if worker
        (try
          (timbre/info (str "Start executing job " worker-name))
          (apply worker args)
          (timbre/info (str "Done executing job " worker-name))
          (catch Throwable e
            (timbre/error (str "Failure executing job " worker-name " with args: " args))
            (timbre/error e)
            (when need-retry?
              (async/<!! (async/timeout retry-delay))
              (enqueue (update-in job-info [:retries] inc)))))
        (recur)))))

(defn- thread-pool-service
  [queue-chan max-threads timeout-ms]
  (let [thread-count (atom 0)
        buffer-chan (async/chan)]
    (async/go (loop []
                (when-let [[job _] (async/alts! [queue-chan executor-ch])]
                  (if (= job :stop)
                    (do
                      (timbre/info "Stopping thread pool")
                      (async/close! buffer-chan))
                    (if-not (async/alt! [[buffer-chan job]] true
                                        :default false)
                      (loop []
                        (if (< @thread-count max-threads)
                          (do (async/put! buffer-chan job)
                              (async/thread
                                (swap! thread-count inc)
                                (execute-job buffer-chan timeout-ms)
                                (swap! thread-count dec)))
                          (when-not (async/alt! [[buffer-chan job]] true
                                                [(async/timeout 1000)] false)
                            (recur)))))))
                (recur))
              (async/close! buffer-chan))))

(defn start! []
  (thread-pool-service queue (default-config :max-threads) (default-config :job-timeout)))

(defn stop! []
  (async/>!! executor-ch :stop))
