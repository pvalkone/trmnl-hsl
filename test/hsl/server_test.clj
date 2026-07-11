(ns hsl.server-test
  "Tests for the HTTP handler."
  (:require [cheshire.core :as json]
            [clojure.test :refer [deftest is]]
            [hsl.config :as config]
            [hsl.render :as render]
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
    (with-redefs [server/fetch-board! (fn [_ _] (throw (ex-info "DT down" {})))
                  server/log-error! (constantly nil)]
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

(deftest internal-error-returns-generic-500
  ;; An unexpected error must not echo the exception message to the client
  (let [state (state-with {"board" nil})]
    (with-redefs [server/fetch-board!
                  (fn [_ _] (throw (ex-info "super secret internal detail" {})))
                  server/log-error! (constantly nil)]
      (let [resp ((server/handler state) {:uri "/api/trmnl/board"})]
        (is (= 500 (:status resp)))
        (is (not (re-find #"super secret internal detail" (:body resp)))
            "does not leak the exception message")
        (is (= "internal server error"
               (:error (json/parse-string (:body resp) true))))))))

(deftest api-route-serves-rendered-markup
  (let [state (state-with {"kotisaarenkatu" {:loaded true}})]
    (with-redefs [server/board-cached! (fn [_] :board)
                  render/render-all (fn [_] {:markup "M"})]
      (let [resp ((server/handler state) {:uri "/api/trmnl/kotisaarenkatu"})]
        (is (= 200 (:status resp)))
        (is (= "application/json; charset=utf-8" (get-in resp [:headers "Content-Type"])))
        (is (= "M" (:markup (json/parse-string (:body resp) true))))))))

(deftest preview-route-serves-html
  (let [state (state-with {"kotisaarenkatu" {:loaded true}})]
    (with-redefs [server/board-cached! (fn [_] :board)
                  render/render-preview (fn [_] "<html>ok</html>")]
      (let [resp ((server/handler state) {:uri "/preview/kotisaarenkatu"})]
        (is (= 200 (:status resp)))
        (is (= "text/html; charset=utf-8" (get-in resp [:headers "Content-Type"])))
        (is (= "<html>ok</html>" (:body resp)))))))

(deftest api-route-unknown-board-404
  (let [state (state-with {"kotisaarenkatu" {:loaded true}})
        resp ((server/handler state) {:uri "/api/trmnl/nope"})]
    (is (= 404 (:status resp)))
    (is (= "unknown board" (:error (json/parse-string (:body resp) true))))))

(deftest preview-route-unknown-board-404
  (let [state (state-with {"kotisaarenkatu" {:loaded true}})
        resp ((server/handler state) {:uri "/preview/nope"})]
    (is (= 404 (:status resp)))
    (is (= "unknown board" (:error (json/parse-string (:body resp) true))))))

(deftest unmatched-route-404
  (let [state (state-with {"kotisaarenkatu" {:loaded true}})
        resp ((server/handler state) {:uri "/nope"})]
    (is (= 404 (:status resp)))
    (is (= "not found" (:error (json/parse-string (:body resp) true))))))

(deftest warm-cache-loads-every-board
  (let [state (state-with {"a" nil "b" nil})]
    (with-redefs [server/fetch-board! (fn [_ _] {:loaded true})
                  server/log! (constantly nil)]
      (server/warm-cache! state)
      (is (every? (fn [h] (some? (:board @(:cache h)))) (vals (:boards state)))
          "every board cache is populated after warming"))))

(deftest warm-cache-tolerates-a-failing-board
  ;; A board that can't load must not abort start-up; it's left empty.
  (let [state (state-with {"a" nil})]
    (with-redefs [server/fetch-board! (fn [_ _] (throw (ex-info "DT down" {})))
                  server/log! (constantly nil)
                  server/log-error! (constantly nil)]
      (server/warm-cache! state)
      (is (nil? (:board @(:cache (get (:boards state) "a"))))))))
