(ns hsl.render-test
  (:require [clojure.test :refer [deftest testing is are]]
            [clojure.string :as str]
            [hsl.render :as render]))

(deftest overflow-label-plural-test
  (are [n expected] (= expected (render/overflow-label n))
    0  nil                            ; no overflow => marker hidden
    -1 nil
    1  "+1 muu häiriötiedote"         ; exactly one extra => singular
    2  "+2 muuta häiriötiedotetta"    ; two or more => partitive plural
    5  "+5 muuta häiriötiedotetta"))

(defn- board-with [alerts]
  {:title "T" :clock "21:35" :date "6.7."
   :left {:stops []} :right {:stops []} :alerts alerts})

(deftest render-caps-and-labels-alerts-test
  (testing "<=2 alerts: all shown, no overflow marker"
    (let [html (render/render-full (board-with ["a" "b"]))]
      (is (str/includes? html "Häiriötiedotteet"))
      (is (not (str/includes? html "muu")))))
  (testing "3 alerts: 2 shown + singular marker"
    (let [html (render/render-full (board-with ["a" "b" "c"]))]
      (is (str/includes? html "+1 muu häiriötiedote"))
      (is (not (str/includes? html "muuta")))))
  (testing "5 alerts: 2 shown + plural marker"
    (let [html (render/render-full (board-with ["a" "b" "c" "d" "e"]))]
      (is (str/includes? html "+3 muuta häiriötiedotetta"))))
  (testing "no alerts: no Häiriötiedotteet heading"
    (is (not (str/includes? (render/render-full (board-with [])) "Häiriötiedotteet")))))

(deftest render-compact-caps-rows-per-layout-test
  (let [many (fn [prefix] (mapv (fn [i] {:line "1" :dest (str prefix i)
                                         :metro false :rt false :hhmm "12:00"})
                                (range 10)))
        board {:title "T" :clock "12:00" :date "6.7."
               :left {:stops [{:name "A" :icon nil :deps (many "AA")}
                              {:name "B" :icon nil :deps (many "BB")}]}
               :right {:stops []} :alerts []}
        rows-shown (fn [layout] (count (re-seq #"(?:AA|BB)\d" (render/render-compact board layout))))]
    (testing "the short layouts cap the two stops to 8 rows total (4 each)"
      (is (= 8 (rows-shown "quadrant")))
      (is (= 8 (rows-shown "half_horizontal"))))
    (testing "the full-height half_vertical allows up to 12 (6 each)"
      (is (= 12 (rows-shown "half_vertical"))))))
