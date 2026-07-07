(ns hsl.board-test
  (:require [clojure.test :refer [deftest testing is]]
            [hsl.board :as board]
            [hsl.config :as config]))

(def service 1751760000) ;; some service-day midnight
(defn- at [dep] (+ service dep))

(defn- stoptime
  ([gtfs-id dep] (stoptime gtfs-id dep {}))
  ([gtfs-id dep {:keys [rt headsign]}]
   {:stop {:gtfsId gtfs-id}
    :realtime (boolean rt)
    :serviceDay service
    :scheduledDeparture dep
    :realtimeDeparture dep
    :headsign (or headsign "")}))

(defn- pattern [gtfs-id short-name headsign dir stoptimes]
  {:pattern {:directionId dir :headsign headsign
             :route {:gtfsId gtfs-id :shortName short-name}}
   :stoptimes stoptimes})

;; Left column: HSL:1230410 has 6 departures, the other two left stops
;; exist so there are 3 names.
(def data
  {:stops
   [{:gtfsId "HSL:1230410" :name "Kotisaarenkatu" :vehicleMode "BUS"
     :alerts [{:alertHeaderText "Bussi 55 poikkeusreitti"}]
     :routes [{:alerts [{:alertHeaderText "Linja 55 myöhässä"}]}
              {:alerts [{:alertHeaderText "Bussi 55 poikkeusreitti"}]}] ; dup
     :stoptimesForPatterns
     [(pattern "HSL:1055" "55" "Keskusta" 0
               [(stoptime "HSL:1230410" 60900)            ;; 17:15
                (stoptime "HSL:1230410" 50700 {:rt true}) ;; 17:05, realtime
                (stoptime "HSL:1230410" 51300)            ;; 17:15-ish
                (stoptime "HSL:1230410" 55000)
                (stoptime "HSL:1230410" 52000)
                (stoptime "HSL:1230410" 53000)])]}
    {:gtfsId "HSL:1210405" :name "Intiankatu" :vehicleMode "BUS"
     :stoptimesForPatterns
     [(pattern "HSL:1050" "50" "Munkkivuori" 0
               [(stoptime "HSL:1210405" 51000)])]}
    {:gtfsId "HSL:1210406" :name "Intiankatu itään" :vehicleMode "BUS"
     :stoptimesForPatterns
     [(pattern "HSL:1051" "51" "Herttoniemi" 1
               [(stoptime "HSL:1210406" 51500)])]}

    ;; Right column
    ;; HSL:1240118: denylist drops 717->Rautatientori dir 1, keeps 999.
    {:gtfsId "HSL:1240118" :name "Kumpulan kampus" :vehicleMode "BUS"
     :stoptimesForPatterns
     [(pattern "HSL:4717" "717" "Rautatientori" 1
               [(stoptime "HSL:1240118" 50000)])   ;; hidden
      (pattern "HSL:9999" "999" "Somewhere" 0
               [(stoptime "HSL:1240118" 50100)])]} ;; kept
    {:gtfsId "HSL:1230112" :name "Kustaa Vaasan tie" :vehicleMode "BUS"
     :stoptimesForPatterns
     [(pattern "HSL:1070" "70" "Rautatientori" 1
               [(stoptime "HSL:1230112" 50200)])]}
    ;; HSL:1230109: allowlist shows ONLY 71->Malmi and 506->Myllypuro (M).
    {:gtfsId "HSL:1230109" :name "Kumpulan kampus (M)" :vehicleMode "BUS"
     :stoptimesForPatterns
     [(pattern "HSL:1071" "71" "Malmi" 0
               [(stoptime "HSL:1230109" 50300)]) ;; allowlisted
      (pattern "HSL:1506" "506" "Myllypuro (M)" 1
               [(stoptime "HSL:1230109" 50400 {:headsign "Myllypuro (M)"})]) ;; allowlisted, metro
      (pattern "HSL:1099" "99" "NotAllowed" 0
               [(stoptime "HSL:1230109" 50500)])]} ;; not allowlisted -> drop
    {:gtfsId "HSL:1240103" :name "Pietari Kalmin katu" :vehicleMode "BUS"
     :stoptimesForPatterns
     [(pattern "HSL:1078" "78" "Latokartano" 0
               [(stoptime "HSL:1240103" 50600)])]}]}) ;; allowlisted

(def now (at 50700)) ;; 17:05
(def result (board/build-board data config/board now))

(defn- stop-by-name [column nm]
  (first (filter #(= (:name %) nm) (:stops column))))

(deftest per-stop-slicing
  (testing "3 left names => floor(12/3)=4 departures per stop, earliest first"
    (let [deps (:deps (stop-by-name (:left result) "Kotisaarenkatu"))]
      (is (= 4 (count deps)))
      (is (= (sort (map :at deps)) (map :at deps)))
      (is (= [(at 50700) (at 51300) (at 52000) (at 53000)] (map :at deps))))))

(deftest realtime-flag
  (testing "realtime departure uses realtimeDeparture and sets rt + ~ hhmm"
    (let [dep (first (:deps (stop-by-name (:left result) "Kotisaarenkatu")))]
      (is (true? (:rt dep)))
      (is (= "17:05" (:hhmm dep))))))

(deftest denylist-hides-route
  (testing "hidden route-key at HSL:1240118 is dropped, others kept"
    (let [deps (:deps (stop-by-name (:right result) "Kumpulan kampus"))
          lines (set (map :line deps))]
      (is (contains? lines "999"))
      (is (not (contains? lines "717"))))))

(deftest allowlist-shows-only-listed
  (testing "HSL:1230109 shows only allowlisted route-keys"
    (let [deps (:deps (stop-by-name (:right result) "Kumpulan kampus (M)"))
          lines (set (map :line deps))]
      (is (= #{"71" "506"} lines))
      (is (not (contains? lines "99"))))))

(deftest metro-and-dest-stripping
  (testing "(M) suffix strips from dest and sets metro"
    (let [dep (first (filter #(= "506" (:line %))
                             (:deps (stop-by-name (:right result) "Kumpulan kampus (M)"))))]
      (is (= "Myllypuro" (:dest dep)))
      (is (true? (:metro dep))))))

(deftest alerts-deduped
  (testing "stop + route alerts are collected and order-preservingly deduped"
    (is (= ["Bussi 55 poikkeusreitti" "Linja 55 myöhässä"] (:alerts result)))))

(deftest board-envelope
  (testing "top-level fields present and correct"
    (is (= "Kotisaarenkatu" (:title result)))
    (is (= now (:generated result)))
    (is (= 10800 (:tz-offset-seconds result)))
    (is (= "17:05" (:clock result)))))
