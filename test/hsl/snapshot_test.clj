(ns hsl.snapshot-test
  "Golden-file snapshot tests for the rendered board markup.

   Each test renders a fixed fixture board through a template and compares the
   output against a committed snapshot in `test/hsl/snapshots`, so any change
   to the templates or render logic that alters the markup is caught as a
   regression.

   After an intentional change, regenerate the snapshots and review the diff:

     UPDATE_SNAPSHOTS=1 bb test"
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [hsl.icons :as icons]
            [hsl.render :as render]))

(defn- dep [line dest {:keys [metro rt hhmm]}]
  {:line line :dest dest :metro (boolean metro) :rt (boolean rt) :hhmm hhmm})

;; A representative board: two columns with several stops, realtime (~) and
;; metro ((M)) marks, a long destination that exercises the preview's one-line
;; clamp, and more alerts than fit so the overflow label renders too.
(def board
  {:title "Testikatu"
   :generated 1751760000
   :tz-offset-seconds 10800
   :clock "17:05"
   :date "6.7."
   :left
   {:stops
    [{:name "Kotisaarenkatu"
      :icon (icons/mode-icons "BUS")
      :deps [(dep "55" "Rautatientori" {:rt true :hhmm "17:05"})
             (dep "55" "Rautatientori" {:hhmm "17:15"})
             (dep "506" "Myllypuro" {:metro true :hhmm "17:20"})]}
     {:name "Intiankatu"
      :icon (icons/mode-icons "TRAM")
      :deps [(dep "57" "Munkkivuori" {:hhmm "17:08"})]}]}
   :right
   {:stops
    [{:name "Kumpulan kampus"
      :icon (icons/mode-icons "SUBWAY")
      :deps [(dep "71" "Malmi" {:rt true :hhmm "17:06"})
             (dep "78" "Rautatientori via Kalasatama ja Sörnäinen" {:hhmm "17:12"})]}]}
   :alerts ["Bussi 55 poikkeusreitti"
            "Linja 506 myöhässä"
            "Pysäkki Kumpula siirtyy"]})

(defn- snapshot-file [name]
  (io/file "test/hsl/snapshots" (str name ".html")))

(defn- check-snapshot
  "Compare `rendered` to the committed snapshot named `name`. With
   UPDATE_SNAPSHOTS set, (re)write the snapshot instead of asserting."
  [name rendered]
  (let [f (snapshot-file name)]
    (if (System/getenv "UPDATE_SNAPSHOTS")
      (do (io/make-parents f)
          (spit f rendered)
          (println "Wrote snapshot" (.getPath f))
          (is true))
      (is (= (when (.exists f) (slurp f)) rendered)
          (str name " markup changed. If intentional, regenerate with "
               "`UPDATE_SNAPSHOTS=1 bb test` and review the diff.")))))

(deftest full-snapshot-test
  (check-snapshot "full" (render/render-full board)))

(deftest compact-snapshot-test
  (check-snapshot "compact" (render/render-compact board)))

(deftest preview-snapshot-test
  (doseq [layout (keys render/preview-layouts)]
    (check-snapshot (str "preview_" layout) (render/render-preview board layout))))
