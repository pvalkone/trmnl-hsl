(ns hsl.validate-test
  (:require [clojure.test :refer [deftest testing is]]
            [hsl.config :as config]
            [hsl.validate :as validate]))

(def board
  {:title "Board"
   :number-of-departures 20
   :columns
   {:left {:rows 12
           :stop-ids ["HSL:1230410" "HSL:1210405"]
           :hidden-routes {}}
    :right {:rows 9
            :stop-ids ["HSL:1240118" "HSL:1230109"]
            :hidden-routes {"HSL:1240118" ["HSL:4717:717:Rautatientori:1"]}
            :show-routes {"HSL:1230109" ["HSL:1506:506:Myllypuro (M):1"]}
            :stop-names {"HSL:1240118" "Kumpulan kampus (länteen)"}}}})

(defn- problems-with [f]
  (validate/problems {"board" (f board)}))

(deftest example-config-is-valid
  (is (empty? (validate/problems (config/read-boards config/example-file)))))

(deftest valid-board-has-no-problems
  (is (empty? (problems-with identity))))

(deftest empty-boards-rejected
  (is (seq (validate/problems {})))
  (is (seq (validate/problems nil))))

(deftest board-level-problems
  (testing "a typo'd key is reported rather than silently ignored"
    (is (= ["board \"board\": unknown key :titel"]
           (problems-with #(assoc % :titel "x")))))
  (testing "missing title and departure count"
    (is (= ["board \"board\" :title: must be a non-blank string"
            "board \"board\" :number-of-departures: must be a positive integer"]
           (problems-with #(dissoc % :title :number-of-departures)))))
  (testing "columns must be exactly :left and :right"
    (is (= ["board \"board\" :columns: must be a map with exactly :left and :right"]
           (problems-with #(update % :columns dissoc :right)))))
  (testing "slug must be URL-safe"
    (is (= ["board \"a/b\": slug must be letters, digits, - or _"]
           (validate/problems {"a/b" board})))))

(deftest column-level-problems
  (testing "rows and stop IDs"
    (is (= ["board \"board\" :left :rows: must be a positive integer"
            "board \"board\" :left :stop-ids: must be a non-empty vector"]
           (problems-with #(update-in % [:columns :left] assoc :rows 0 :stop-ids [])))))
  (testing "duplicate stop IDs"
    (is (= ["board \"board\" :left :stop-ids: contains duplicates"]
           (problems-with #(assoc-in % [:columns :left :stop-ids]
                                     ["HSL:1230410" "HSL:1230410"])))))
  (testing "filters must refer to the column's own stops"
    (is (= ["board \"board\" :right :hidden-routes: \"HSL:1230410\" is not in :stop-ids"]
           (problems-with #(assoc-in % [:columns :right :hidden-routes]
                                     {"HSL:1230410" ["HSL:1055:55:Koskela:0"]})))))
  (testing "route keys must look like route keys"
    (is (= ["board \"board\" :right :show-routes \"HSL:1230109\": must be a list of route keys"]
           (problems-with #(assoc-in % [:columns :right :show-routes]
                                     {"HSL:1230109" ["506"]}))))))

(deftest validate!-throws-with-every-problem
  (let [e (try (validate/validate! {"board" (dissoc board :title :number-of-departures)})
               nil
               (catch clojure.lang.ExceptionInfo e e))]
    (is (some? e))
    (is (= 2 (count (:problems (ex-data e)))))))
