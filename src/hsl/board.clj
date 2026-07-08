(ns hsl.board
  "Transforms Digitransit GraphQL `data` to the board map the templates
   render."
  (:require [clojure.string :as str])
  (:import (java.time Instant ZoneId ZonedDateTime)
           (java.time.format DateTimeFormatter)))

(def zone (ZoneId/of "Europe/Helsinki"))
(def ^:private hhmm-fmt (DateTimeFormatter/ofPattern "HH:mm"))

(defn- zoned ^ZonedDateTime [epoch-seconds]
  (ZonedDateTime/ofInstant (Instant/ofEpochSecond epoch-seconds) zone))

(defn hhmm
  "Helsinki-local HH:mm for an absolute epoch second."
  [epoch-seconds]
  (.format (zoned epoch-seconds) hhmm-fmt))

(defn tz-offset-seconds
  "Seconds east of UTC for the Helsinki zone at `epoch-seconds` (DST-aware)."
  [epoch-seconds]
  (.. (zoned epoch-seconds) getOffset getTotalSeconds))

(defn dedup
  "Order-preserving distinct."
  [xs]
  (vec (distinct xs)))

(defn route-key
  "\"<routeGtfsId>:<shortName>:<patternHeadsign>:<directionId>\" for a pattern."
  [pattern]
  (str (get-in pattern [:route :gtfsId]) ":"
       (get-in pattern [:route :shortName]) ":"
       (:headsign pattern) ":"
       (:directionId pattern)))

(defn stop-alerts
  "Deduped alert header texts (stop-level + route-level) across `ids`."
  [by-id ids]
  (dedup
   (for [sid ids
         :let [stop (get by-id sid)]
         :when stop
         text (concat (keep :alertHeaderText (:alerts stop))
                      (mapcat #(keep :alertHeaderText (:alerts %)) (:routes stop)))]
     text)))

(defn- visible?
  "True when route-key `rk` passes a stop's allow/deny lists. `show` is nil (no
   allowlist) or a set of permitted keys; `hidden` is a set of denied keys."
  [show hidden rk]
  (and (or (nil? show) (contains? show rk))
       (not (contains? hidden rk))))

(defn- departure-at
  "Absolute epoch second of stoptime `st`: realtime when live, else scheduled."
  [st]
  (+ (:serviceDay st)
     (if (:realtime st) (:realtimeDeparture st) (:scheduledDeparture st))))

(defn- ->departure
  "Board departure entry for stoptime `st` of pattern `stp` departing `stop`."
  [stop stp st]
  (let [headsign (get-in stp [:pattern :headsign])
        at (departure-at st)]
    {:name (:name stop)
     :line (get-in stp [:pattern :route :shortName])
     :dest (-> headsign (str/replace #"\s*\(M\)\s*$" "") str/trim)
     :metro (or (str/includes? (str (:headsign st)) "(M)")
                (str/includes? (str headsign) "(M)"))
     :at at
     :hhmm (hhmm at)
     :rt (boolean (:realtime st))
     :mode (:vehicleMode stop)}))

(defn- departures
  "All departures for `col`, before per-stop grouping/slicing. Each carries its
   originating stop `:name` so build-column can group by it."
  [by-id col]
  (for [sid (:stop-ids col)
        :let [stop (get by-id sid)]
        :when stop
        :let [hidden (set (get (:hidden-routes col) sid []))
              show (some-> (:show-routes col) (get sid) set)] ;; nil -> no allowlist
        stp (:stoptimesForPatterns stop)
        st (:stoptimes stp)
        :when (= (get-in st [:stop :gtfsId]) sid)
        :when (visible? show hidden (route-key (:pattern stp)))]
    (->departure stop stp st)))

(defn build-column
  "One column: departures grouped by stop name, each stop's list sorted by time
   and evenly capped so the column fits `:rows`."
  [by-id col]
  (let [deps (departures by-id col)
        names (dedup (for [sid (:stop-ids col)
                           :let [stop (get by-id sid)]
                           :when stop]
                       (:name stop)))
        per-stop (max 1 (long (Math/floor (/ (:rows col) (double (max 1 (count names)))))))]
    {:stops (vec (for [nm names]
                   {:name nm
                    :deps (->> deps
                               (filter #(= (:name %) nm))
                               (sort-by :at)
                               (take per-stop)
                               (mapv #(dissoc % :name)))}))}))

(defn index-by-gtfs-id
  "Map gtfsId -> stop, from the GraphQL `data.stops` list."
  [data]
  (into {} (map (juxt :gtfsId identity)) (:stops data)))

(defn build-board
  "Assemble the full board map from GraphQL `data`, board `config` and the
   generation instant `now`."
  [data config now]
  (let [by-id (index-by-gtfs-id data)
        left-col (get-in config [:columns :left])
        right-col (get-in config [:columns :right])
        local (zoned now)]
    {:title (:title config)
     :generated now
     :tz-offset-seconds (tz-offset-seconds now)
     :clock (.format local hhmm-fmt)
     :date (str (.getDayOfMonth local) "." (.getMonthValue local) ".")
     :left (build-column by-id left-col)
     :right (build-column by-id right-col)
     :alerts (stop-alerts by-id (into (:stop-ids left-col) (:stop-ids right-col)))}))
