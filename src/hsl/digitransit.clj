(ns hsl.digitransit
  "Fetches HSL departures from Digitransit's public GraphQL API."

  (:require [babashka.http-client :as http]
            [cheshire.core :as json]))

(def default-endpoint "https://api.digitransit.fi/routing/v2/hsl/gtfs/v1")

;; Bound the outbound call so a slow or unresponsive upstream can't wedge a
;; request thread indefinitely. A timeout surfaces as an exception, which the
;; caller turns into a last-good (stale-cache) response.
(def default-connect-timeout-ms 5000)
(def default-request-timeout-ms 10000)

(def query
  "query GetDeparturesForStops($ids: [String!]!, $numberOfDepartures: Int!) {
  stops: stops(ids: $ids) {
    name
    code
    gtfsId
    vehicleMode
    alerts { alertHeaderText }
    routes { gtfsId alerts { alertHeaderText } }
    stoptimesForPatterns(numberOfDepartures: $numberOfDepartures, omitCanceled: false) {
      pattern {
        directionId
        headsign
        route { gtfsId shortName }
      }
      stoptimes {
        stop { gtfsId }
        realtime
        serviceDay
        scheduledDeparture
        realtimeDeparture
        headsign
      }
    }
  }
}")

(defn fetch!
  "POST the departures query for `ids` and return the parsed `data` map.
   Throws (ex-info) on transport failure or GraphQL `errors`, and a
   `HttpTimeoutException` if the upstream is too slow.

   Optional `:endpoint`, `:connect-timeout-ms` and `:request-timeout-ms`
   override the defaults (mainly for tests)."
  [{:keys [api-key ids number-of-departures endpoint
           connect-timeout-ms request-timeout-ms]}]
  (let [endpoint (or endpoint default-endpoint)
        connect-timeout-ms (or connect-timeout-ms default-connect-timeout-ms)
        request-timeout-ms (or request-timeout-ms default-request-timeout-ms)
        body (json/generate-string
              {:operationName "GetDeparturesForStops"
               :variables {:ids ids :numberOfDepartures number-of-departures}
               :query query})
        client (http/client {:connect-timeout connect-timeout-ms})
        resp (http/post endpoint
                        {:client client
                         :timeout request-timeout-ms
                         :headers {"Content-Type" "application/json"
                                   "digitransit-subscription-key" api-key}
                         :body body})
        parsed (json/parse-string (:body resp) true)]
    (when-let [errors (:errors parsed)]
      (throw (ex-info "Digitransit GraphQL errors" {:errors errors})))
    (:data parsed)))
