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
