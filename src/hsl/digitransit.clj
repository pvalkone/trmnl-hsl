(ns hsl.digitransit
  "Fetches HSL departures from Digitransit's public GraphQL API."

  (:require [babashka.http-client :as http]
            [cheshire.core :as json]))

(def endpoint "https://api.digitransit.fi/routing/v2/hsl/gtfs/v1")

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
   Throws (ex-info) on transport failure or GraphQL `errors`."
  [{:keys [api-key ids number-of-departures]}]
  (let [body (json/generate-string
              {:operationName "GetDeparturesForStops"
               :variables {:ids ids :numberOfDepartures number-of-departures}
               :query query})
        resp (http/post endpoint
                        {:headers {"Content-Type" "application/json"
                                   "digitransit-subscription-key" api-key}
                         :body body})
        parsed (json/parse-string (:body resp) true)]
    (when-let [errors (:errors parsed)]
      (throw (ex-info "Digitransit GraphQL errors" {:errors errors})))
    (:data parsed)))
