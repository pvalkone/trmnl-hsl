(ns hsl.digitransit-test
  "Tests for the Digitransit client."
  (:require [cheshire.core :as json]
            [clojure.test :refer [deftest is]]
            [hsl.digitransit :as digitransit]
            [org.httpkit.server :as http])
  (:import (clojure.lang ExceptionInfo)
           (java.net.http HttpTimeoutException)))

(defn- with-server
  "Run `handler` on an ephemeral port, call `f` with its base URL and stop it."
  [handler f]
  (let [stop (http/run-server handler {:port 0})]
    (try
      (f (str "http://localhost:" (:local-port (meta stop)) "/"))
      (finally (stop)))))

(deftest fetch-returns-data
  (with-server
    (fn [_] {:status 200
             :headers {"Content-Type" "application/json"}
             :body (json/generate-string {:data {:stops []}})})
    (fn [url]
      (is (= {:stops []}
             (digitransit/fetch! {:api-key "x" :ids [] :number-of-departures 1
                                  :endpoint url}))))))

(deftest fetch-throws-on-graphql-errors
  (with-server
    (fn [_] {:status 200
             :headers {"Content-Type" "application/json"}
             :body (json/generate-string {:errors [{:message "boom"}]})})
    (fn [url]
      (is (thrown-with-msg? ExceptionInfo #"GraphQL errors"
                            (digitransit/fetch! {:api-key "x" :ids [] :number-of-departures 1
                                                 :endpoint url}))))))

(deftest fetch-times-out-on-slow-upstream
  ;; An upstream slower than the request timeout must surface as an exception
  ;; so the caller can fall back to the stale cache, not hang the thread.
  (with-server
    ;; Swallow the interrupt from stopping the server mid-sleep so http-kit
    ;; doesn't log the (expected) InterruptedException as an error.
    (fn [_] (try (Thread/sleep 2000) (catch InterruptedException _ nil))
      {:status 200 :body "{}"})
    (fn [url]
      (is (thrown? HttpTimeoutException
                   (digitransit/fetch! {:api-key "x" :ids ["HSL:1"] :number-of-departures 1
                                        :endpoint url :request-timeout-ms 150}))))))
