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
  "The device layouts a browser preview can render. Each carries what the
   preview shell needs: the TRMNL `view--` modifier, whether it embeds the
   compact stand-in, and, for the sub-full layouts, the mashup wrapper plus
   the number of empty placeholder regions that fill out the rest of the
   screen, so the layout previews at its true on-device size the way a
   device mashup arranges it."
  {"full"            {:view "full"}
   "half_horizontal" {:view "half_horizontal"
                      :compact? true
                      :mashup "mashup--1Tx1B"
                      :placeholders 1}
   "half_vertical"   {:view "half_vertical"
                      :compact? true
                      :mashup "mashup--1Lx1R"
                      :placeholders 1}
   "quadrant"        {:view "quadrant"
                      :compact? true
                      :mashup "mashup--2x2"
                      :placeholders 3}})

(defn render-preview
  "A standalone HTML page wrapping `board` in the TRMNL design framework (its CSS
   and a `screen`/`view` shell) for `layout`. Sub-full layouts render inside a
   mashup with placeholder regions, so /preview shows the layout at its true
   on-device size instead of a bare, full-width box."
  [board layout]
  (let [{:keys [view compact? mashup placeholders]} (preview-layouts layout)]
    (selmer/render-file "preview.html"
                        (assoc (context board)
                               :view view
                               :compact compact?
                               :mashup mashup
                               :placeholders (range (or placeholders 0))))))

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
