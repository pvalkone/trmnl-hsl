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
           :alerts_count (count alerts)
           :alerts_label (alerts-label (count alerts))
           :alerts_icon icons/alerts-icon)))

(defn render-full
  "Markup for the device's full layout."
  [board]
  (selmer/render-file "full.html" (context board)))

(def compact-rows
  "Max departure rows the compact stand-in shows per device layout, so its
   content fits the layout's cell instead of overflowing. `:left`/`:right` are
   the per-column caps; only the wide half_horizontal shows a `:right` column.
   The left column caps lower where it carries more stop headings."
  {"half_horizontal" {:left 6 :right 8}
   "half_vertical"   {:left 12}
   "quadrant"        {:left 8}})

(defn- narrow-bar?
  "The half-width layouts, whose title bar can't fit the full alert label."
  [layout]
  (contains? #{"half_vertical" "quadrant"} layout))

(defn- cap-stops
  "Trim `stops` to at most `n` departure rows, split evenly across the stops, so
   a compact column fits a short layout's cell."
  [stops n]
  (let [per-stop (max 1 (quot n (max 1 (count stops))))]
    (mapv (fn [stop]
            (update stop :deps #(vec (take per-stop %))))
          stops)))

(defn- compact-context
  "Template context for the compact stand-in in `layout`: the columns it renders,
   each capped to the rows that fit with a short-badge flag and possible column
   headers."
  [board layout]
  (let [rows (compact-rows layout)
        left (cap-stops (get-in board [:left :stops]) (:left rows))
        cols (if (:right rows)
               [left (cap-stops (get-in board [:right :stops]) (:right rows))]
               [left])]
    (assoc (context board)
           :cols cols
           :alerts_badge_short (narrow-bar? layout)
           :column_headers (= layout "half_vertical"))))

(defn render-compact
  "Single-column markup for `layout` (half_horizontal/half_vertical/quadrant),
   trimmed to the rows that fit its cell."
  [board layout]
  (selmer/render-file "compact.html" (compact-context board layout)))

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
  (let [{:keys [view compact? mashup placeholders]} (preview-layouts layout)
        base (if compact? (compact-context board layout) (context board))]
    (selmer/render-file "preview.html"
                        (assoc base
                               :view view
                               :compact compact?
                               :mashup mashup
                               :placeholders (range (or placeholders 0))))))

(defn render-all
  "The flat JSON payload TRMNL consumes: one entry per layout. The half/quadrant
   layouts reuse the compact rendering, each capped to the rows that fit it."
  [board]
  {:markup (render-full board)
   :markup_half_horizontal (render-compact board "half_horizontal")
   :markup_half_vertical (render-compact board "half_vertical")
   :markup_quadrant (render-compact board "quadrant")})
