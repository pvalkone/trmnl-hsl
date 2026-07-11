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
