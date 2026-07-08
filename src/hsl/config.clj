(ns hsl.config)

;;; Static board config, captured originally from the real Omat pysäkkinäytöt board's
;;; /api/staticmonitor/<uuid>.
;;;
;;; Route keys ("<routeGtfsId>:<shortName>:<patternHeadsign>:<directionId>") are
;;; matched against the pattern's bare headsign (not the stoptime's "via X" variant).
;;;
;;; :hidden-routes {stopId [...]} ; Denylist: drop these route patterns at a given stop
;;; :show-routes   {stopId [...]} ; Allowlist: at these stops, show only the listed patterns

(def board
  {:title "Kotisaarenkatu"
   :number-of-departures 20
   :columns
   {:left
    {:rows 12
     :stop-ids ["HSL:1230410" "HSL:1210405" "HSL:1210406"]
     :hidden-routes {}}
    :right
    {:rows 9
     :stop-ids ["HSL:1240118" "HSL:1230112" "HSL:1230109" "HSL:1240103"]
     :hidden-routes
     {"HSL:1240118"
      ["HSL:4711K:711K:Hakaniemi:1" "HSL:4717:717:Rautatientori:1"
       "HSL:4717K:717K:Rautatientori:1" "HSL:4717N:717N:Rautatientori:1"
       "HSL:4721:721:Hakaniemi:1" "HSL:4721N:721N:Rautatientori:1"
       "HSL:1073:73:Hakaniemi:1" "HSL:4731:731:Hakaniemi:1"
       "HSL:4731N:731N:Rautatientori:1" "HSL:4739:739:Rautatientori:1"
       "HSL:1073N:73N:Rautatientori:1" "HSL:1075:75:Rautatientori:1"
       "HSL:1077:77:Rautatientori:1" "HSL:1078:78:Rautatientori:1"
       "HSL:9788K:788K:Rautatientori:1" "HSL:1079N:79N:Rautatientori:1"
       "HSL:1074:74:Hakaniemi:1" "HSL:1074N:74N:Rautatientori:1"]
      "HSL:1230112"
      ["HSL:1506:506:Ruskeasuo:0" "HSL:1077N:77N:Rautatientori:1"
       "HSL:1078N:78N:Rautatientori:1"]}
     :show-routes
     {"HSL:1230109" ["HSL:1071:71:Malmi:0" "HSL:1506:506:Myllypuro (M):1"]
      "HSL:1240103" ["HSL:1078:78:Latokartano:0"]}}}})

(defn stop-ids
  "All stop IDs across both columns, in board order."
  [board]
  (into (get-in board [:columns :left :stop-ids])
        (get-in board [:columns :right :stop-ids])))
