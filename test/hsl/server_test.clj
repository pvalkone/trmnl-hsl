(ns hsl.server-test
  "Tests for the HTTP handler."
  (:require [cheshire.core :as json]
            [clojure.test :refer [deftest is]]
            [hsl.config :as config]
            [hsl.server :as server]))

(defn- state-with
  "A server state from a slug->board map, reusing a real board config for each
   handle. A nil board means that board has never loaded."
  [slug->board]
  (let [[_ cfg] (first config/boards)]
    {:boards (into {} (for [[slug board] slug->board]
                        [slug {:cache (atom {:board board :fetched-at-ms 0})
                               :config cfg}]))
     :api-key "x"
     :ttl-ms 60000}))

(defn- health [state]
  (let [resp ((server/handler state) {:uri "/health"})]
    (assoc resp :parsed (json/parse-string (:body resp) true))))

(deftest health-ok-when-all-boards-loaded
  (let [{:keys [status parsed]} (health (state-with {"a" {:loaded true}}))]
    (is (= 200 status))
    (is (= "ok" (:status parsed)))))

(deftest health-degraded-when-no-board-loaded
  (let [{:keys [status parsed]} (health (state-with {"a" nil}))]
    (is (= 503 status))
    (is (= "degraded" (:status parsed)))))

(deftest health-ok-when-any-board-loaded
  ;; One loaded, one not: serving any board proves the service works.
  (let [{:keys [status parsed]} (health (state-with {"a" {:loaded true} "b" nil}))]
    (is (= 200 status))
    (is (= "ok" (:status parsed)))))

(defn- handle-with-cache
  "A board handle whose cache atom starts as `cache-contents`."
  [cache-contents]
  (let [[_ cfg] (first config/boards)]
    {:cache (atom cache-contents) :config cfg :api-key "x" :ttl-ms 60000}))

(deftest refetch-failure-serves-stale-and-flags-cache
  (let [{:keys [cache] :as handle} (handle-with-cache {:board {:loaded true}
                                                       :fetched-at-ms 0})]
    (with-redefs [server/fetch-board! (fn [_ _] (throw (ex-info "DT down" {})))]
      (is (= {:loaded true} (server/board-cached! handle)) "serves the last-good board")
      (is (:refresh-failing? @cache) "flags the cache as failing"))))

(deftest successful-refetch-clears-failing-flag
  (let [{:keys [cache] :as handle} (handle-with-cache {:board {:stale true}
                                                       :fetched-at-ms 0
                                                       :refresh-failing? true})]
    (with-redefs [server/fetch-board! (fn [_ _] {:fresh true})]
      (is (= {:fresh true} (server/board-cached! handle)))
      (is (nil? (:refresh-failing? @cache)) "a good refetch clears the flag"))))

(deftest health-degraded-when-only-board-is-refresh-failing
  (let [[_ cfg] (first config/boards)
        state {:boards {"a" {:cache (atom {:board {:loaded true}
                                           :fetched-at-ms 0
                                           :refresh-failing? true})
                             :config cfg}}
               :api-key "x" :ttl-ms 60000}
        {:keys [status parsed]} (health state)]
    (is (= 503 status))
    (is (= "degraded" (:status parsed)))
    (is (true? (get-in parsed [:boards :a :refresh_failing])))))

(deftest health-ok-when-a-board-serves-despite-another-failing
  (let [[_ cfg] (first config/boards)
        state {:boards {"a" {:cache (atom {:board {:loaded true} :fetched-at-ms 0})
                             :config cfg}
                        "b" {:cache (atom {:board {:loaded true}
                                           :fetched-at-ms 0
                                           :refresh-failing? true})
                             :config cfg}}
               :api-key "x" :ttl-ms 60000}
        {:keys [status parsed]} (health state)]
    (is (= 200 status))
    (is (= "ok" (:status parsed)))))
