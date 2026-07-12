(ns hsl.board
  "Transforms Digitransit GraphQL `data` to the board map the templates
   render."
  (:require [clojure.string :as str]
            [hsl.icons :as icons])
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
  "Deduped alert header texts across the board's `stops` — an ordered seq of
   `[stop-id visible-route-gtfsIds]`. Includes every stop's own alerts, plus
   route-level alerts only for routes with a visible pattern at that stop."
  [by-id stops]
  (dedup
   (for [[sid route-ids] stops
         :let [stop (get by-id sid)]
         :when stop
         text (concat (keep :alertHeaderText (:alerts stop))
                      (for [route (:routes stop)
                            :when (contains? route-ids (:gtfsId route))
                            t (keep :alertHeaderText (:alerts route))]
                        t))]
     text)))

(defn- visible?
  "True when route-key `rk` passes a stop's allow/deny lists. `show` is nil (no
   allowlist) or a set of permitted keys; `hidden` is a set of denied keys."
  [show hidden rk]
  (and (or (nil? show) (contains? show rk))
       (not (contains? hidden rk))))

(defn- visible-route-ids
  "gtfsIds of routes at `sid` in column `col` with at least one visible pattern,
   i.e. the routes actually shown for that stop."
  [by-id col sid]
  (let [stop (get by-id sid)
        hidden (set (get (:hidden-routes col) sid []))
        show (some-> (:show-routes col) (get sid) set)]
    (into #{}
          (for [stp (:stoptimesForPatterns stop)
                :when (visible? show hidden (route-key (:pattern stp)))]
            (get-in stp [:pattern :route :gtfsId])))))

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

(defn stop-name
  "Display name for `sid`: the config `:stop-names` override, else the stop's
   Digitransit name. Overrides let same-named stops (e.g. three \"Tekniikan
   museo\" stops on different streets) group and label distinctly."
  [col by-id sid]
  (get (:stop-names col) sid (:name (get by-id sid))))

(defn- departures
  "All departures for `col`, before per-stop grouping/slicing. Each carries its
   originating stop's display `:name` so build-column can group by it."
  [by-id col]
  (for [sid (:stop-ids col)
        :let [stop (get by-id sid)]
        :when stop
        :let [hidden (set (get (:hidden-routes col) sid []))
              show (some-> (:show-routes col) (get sid) set) ; nil -> no allowlist
              nm (stop-name col by-id sid)]
        stp (:stoptimesForPatterns stop)
        st (:stoptimes stp)
        :when (= (get-in st [:stop :gtfsId]) sid)
        :when (visible? show hidden (route-key (:pattern stp)))]
    (assoc (->departure stop stp st) :name nm)))

(defn build-column
  "One column: departures grouped by stop name, each stop's list sorted by time
   and evenly capped so the column fits `:rows`. Each stop carries an `:icon`
   for its mode that comes from its first (earliest) departure, so a mixed-mode
   stop will show only that departure's icon."
  [by-id col]
  (let [deps (departures by-id col)
        names (dedup (for [sid (:stop-ids col)
                           :when (get by-id sid)]
                       (stop-name col by-id sid)))
        per-stop (max 1 (quot (:rows col) (max 1 (count names))))]
    {:stops (vec (for [nm names
                       :let [nm-deps (->> deps
                                          (filter #(= (:name %) nm))
                                          (sort-by :at)
                                          (take per-stop))]]
                   {:name nm
                    :icon (icons/mode-icons (:mode (first nm-deps)))
                    :deps (mapv #(dissoc % :name) nm-deps)}))}))

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
        alert-stops (into (mapv (fn [sid] [sid (visible-route-ids by-id left-col sid)])
                                (:stop-ids left-col))
                          (mapv (fn [sid] [sid (visible-route-ids by-id right-col sid)])
                                (:stop-ids right-col)))
        local (zoned now)]
    {:title (:title config)
     :generated now
     :tz-offset-seconds (tz-offset-seconds now)
     :clock (.format local hhmm-fmt)
     :date (str (.getDayOfMonth local) "." (.getMonthValue local) ".")
     :left (build-column by-id left-col)
     :right (build-column by-id right-col)
     :alerts (stop-alerts by-id alert-stops)}))
