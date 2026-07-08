(ns hsl.server
  "Always-on HTTP server: fetches and transforms the HSL departure board
   behind a short-TTL cache and serves TRMNL-ready markup."
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hsl.board :as board]
            [hsl.config :as config]
            [hsl.digitransit :as digitransit]
            [hsl.render :as render]
            [org.httpkit.server :as http])
  (:import (java.time ZonedDateTime)
           (java.time.format DateTimeFormatter)))

(defn- now-ms [] (System/currentTimeMillis))
(defn- now-seconds [] (quot (now-ms) 1000))

(def ^:private log-formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))

(defn- timestamp []
  (.format (ZonedDateTime/now board/zone) log-formatter))

(defn log
  "Print a timestamped line to stdout."
  [& args]
  (println (str (timestamp) " " (str/join " " args))))

(defn log-error
  "Like `log`, but to stderr."
  [& args]
  (binding [*out* *err*]
    (println (str (timestamp) " " (str/join " " args)))))

(defn load-dotenv!
  "Load KEY=VALUE lines from an .env file into the process env view we use.
   Explicit env vars still win, we only fill blanks."
  [path]
  (let [f (io/file path)]
    (when (.exists f)
      (into {}
            (for [line (str/split-lines (slurp f))
                  :let [line (str/trim line)]
                  :when (and (seq line) (not (str/starts-with? line "#")) (str/includes? line "="))
                  :let [[k v] (str/split line #"=" 2)]]
              [(str/trim k) (str/trim v)])))))

(defn env
  "Env var lookup falling back to the loaded .env map, then `default`."
  [dotenv k default]
  (or (System/getenv k) (get dotenv k) default))

;; Digitransit subscription keys are Azure APIM keys: 32 hex chars
(def api-key-pattern #"(?i)[0-9a-f]{32}")

(defn validate-api-key
  "Return the key, or throw with a clear message if it isn't a well-formed
   Digitransit subscription key."
  [api-key]
  (cond
    (str/blank? api-key)
    (throw (ex-info "DIGITRANSIT_KEY is required." {}))

    (not (re-matches api-key-pattern api-key))
    (throw (ex-info "DIGITRANSIT_KEY is not a valid Digitransit subscription key."
                    {}))

    :else api-key))

(defn fetch-board!
  "Fetch fresh data from Digitransit and build the board map."
  [api-key]
  (let [data (digitransit/fetch! {:api-key api-key
                                  :ids (config/stop-ids config/board)
                                  :number-of-departures (:number-of-departures config/board)})]
    (board/build-board data config/board (now-seconds))))

(defn board-cached!
  "Return a board map, refetching when the cache is older than `ttl-ms`.

   On a refetch failure, serve the last-good board (logging the error);
   only propagate if we have never had a good board."
  [{:keys [cache api-key ttl-ms]}]
  (let [{:keys [board fetched-at-ms]} @cache
        stale? (or (nil? board) (> (- (now-ms) fetched-at-ms) ttl-ms))]
    (if-not stale?
      board
      (try
        (let [fresh (fetch-board! api-key)]
          (reset! cache {:board fresh :fetched-at-ms (now-ms)})
          fresh)
        (catch Exception e
          (log-error "Board refetch failed:" (ex-message e))
          (or board (throw e)))))))

(defn- json-response [status body]
  {:status status
   :headers {"Content-Type" "application/json; charset=utf-8"}
   :body (json/generate-string body)})

(defn handler [state]
  (fn [{:keys [uri]}]
    (try
      (case uri
        "/api/trmnl"
        (json-response 200 (render/render-all (board-cached! state)))

        "/preview"
        {:status 200
         :headers {"Content-Type" "text/html; charset=utf-8"}
         :body (render/render-full (board-cached! state))}

        "/health"
        (let [{:keys [board fetched-at-ms]} @(:cache state)]
          (json-response 200 {:status "ok"
                              :title (:title config/board)
                              :stops (config/stop-ids config/board)
                              :cached_at fetched-at-ms
                              :has_board (some? board)}))

        (json-response 404 {:error "not found"}))
      (catch Exception e
        (log-error "Request error:" (ex-message e))
        (json-response 500 {:error (ex-message e)})))))

(defn -main [& _]
  (let [dotenv (load-dotenv! ".env")
        api-key (validate-api-key (env dotenv "DIGITRANSIT_KEY" nil))
        port (Long/parseLong (env dotenv "PORT" "4001"))
        ttl-ms (Long/parseLong (env dotenv "CACHE_TTL_MS" "60000"))
        state {:cache (atom {:board nil :fetched-at-ms 0})
               :api-key api-key
               :ttl-ms ttl-ms}
        stop-server (http/run-server (handler state) {:port port})]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable (fn []
                                           (log "Server shutting down")
                                           (stop-server))))
    (log (str "Server started on port " port " (TTL " ttl-ms "ms)"))
    @(promise)))
