(ns hsl.render-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [hsl.render :as render]))

(deftest overflow-label-plural-test
  (testing "no overflow => nil (marker hidden)"
    (is (nil? (render/overflow-label 0)))
    (is (nil? (render/overflow-label -1))))
  (testing "exactly one extra => singular"
    (is (= "+1 muu häiriötiedote" (render/overflow-label 1))))
  (testing "two or more => partitive plural"
    (is (= "+2 muuta häiriötiedotetta" (render/overflow-label 2)))
    (is (= "+5 muuta häiriötiedotetta" (render/overflow-label 5)))))

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
