(ns hsl.render
  "Renders the board map into TRMNL layout markup with Selmer."
  (:require [hsl.icons :as icons]
            [selmer.parser :as selmer]))

(def ^:private max-alerts 2)

(defn overflow-label
  "Finnish overflow marker for `n` hidden alerts."
  [n]
  (when (pos? n)
    (if (= n 1)
      (str "+" n " muu häiriötiedote")
      (str "+" n " muuta häiriötiedotetta"))))

(defn alerts-label
  "A alert count summary for the compact layouts' indicator."
  [n]
  (str n (if (= n 1) " häiriötiedote" " häiriötiedotetta")))

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
           :alerts_overflow (overflow-label extra)
           :alerts_label (alerts-label (count alerts))
           :alerts_icon icons/alerts-icon)))

(defn render-full
  "Markup for the device's full layout."
  [board]
  (selmer/render-file "full.html" (context board)))

(defn render-compact
  "Single-column markup reused for the half and quadrant layouts."
  [board]
  (selmer/render-file "compact.html" (context board)))

(def preview-layouts
  "The device layouts a browser preview can render, in `screen`/`view` order.
   Each maps its TRMNL `view--` modifier to the partial it embeds: `full` uses
   the two-column board, the smaller layouts reuse the compact stand-in."
  {"full"            "full.html"
   "half_horizontal" "compact.html"
   "half_vertical"   "compact.html"
   "quadrant"        "compact.html"})

(defn render-preview
  "A standalone HTML page wrapping `board` in the TRMNL design framework (its CSS
   and a `screen`/`view` shell) for `layout`, so /preview renders in a browser the
   way the plugin renders on the device."
  [board layout]
  (selmer/render-file "preview.html"
                      (assoc (context board)
                             :view layout
                             :compact (= (preview-layouts layout) "compact.html"))))

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
