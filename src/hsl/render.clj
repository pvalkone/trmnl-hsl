(ns hsl.render
  "Renders the board map into TRMNL layout markup with Selmer."
  (:require [selmer.parser :as selmer]))

(def ^:private max-alerts 3)

(defn overflow-label
  "Finnish overflow marker for `n` hidden alerts."
  [n]
  (when (pos? n)
    (if (= n 1)
      (str "+" n " muu häiriötiedote")
      (str "+" n " muuta häiriötiedotetta"))))

(defn- context
  "Template context: the board map plus alerts capped at `max-alerts`, with the
   overflow rendered as a ready-to-print `alerts_overflow` string."
  [board]
  (let [alerts (:alerts board)
        extra (max 0 (- (count alerts) max-alerts))]
    (assoc board
           ;; nil (not []) when empty: Selmer treats an empty collection as
           ;; truthy, so `{% if alerts %}` would otherwise show the heading
           :alerts (not-empty (vec (take max-alerts alerts)))
           :alerts_overflow (overflow-label extra))))

(defn render-full [board]
  (selmer/render-file "full.html" (context board)))

(defn render-compact [board]
  (selmer/render-file "compact.html" (context board)))

(defn render-all
  "The flat JSON payload TRMNL consumes: one entry per layout. The half/quadrant
   layouts reuse the compact rendering."
  [board]
  (let [full (render-full board)
        compact (render-compact board)]
    {:markup full
     :markup_half_horizontal compact
     :markup_half_vertical compact
     :markup_quadrant compact}))
