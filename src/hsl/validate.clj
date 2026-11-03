(ns hsl.validate
  "Structural checks for `hsl.config/boards`, so a broken config is caught by
   `bb check` before a restart."
  (:require [clojure.string :as str]))

(def ^:private board-keys #{:title :number-of-departures :columns})
(def ^:private column-keys #{:rows :stop-ids :stop-names :hidden-routes :show-routes})
(def ^:private slug-pattern #"[A-Za-z0-9_-]+")
(def ^:private stop-id-pattern #"[A-Za-z]+:\S+")
(def ^:private route-key-pattern #".+:.+:.+:\d+")

(defn- unknown-keys [where m allowed]
  (for [k (sort-by str (remove allowed (keys m)))]
    (str where ": unknown key " (pr-str k))))

(defn- stop-keyed-problems
  "Problems with a stop-keyed map such as `:hidden-routes`, whose keys must be
   stop IDs of this column and whose values must satisfy `valid-value?`."
  [where k m stop-ids valid-value? value-desc]
  (cond
    (nil? m) []
    (not (map? m)) [(str where " " k ": must be a map keyed by stop ID")]
    :else
    (for [[sid v] m
          problem [(when-not (contains? stop-ids sid)
                     (str where " " k ": " (pr-str sid) " is not in :stop-ids"))
                   (when-not (valid-value? v)
                     (str where " " k " " (pr-str sid) ": must be " value-desc))]
          :when problem]
      problem)))

(defn- route-keys? [v]
  (and (sequential? v)
       (every? #(and (string? %) (re-matches route-key-pattern %)) v)))

(defn- column-problems [where col]
  (if-not (map? col)
    [(str where ": must be a map")]
    (let [{:keys [rows stop-ids]} col
          stop-id-set (if (sequential? stop-ids) (set stop-ids) #{})]
      (concat
       (unknown-keys where col column-keys)
       (when-not (pos-int? rows)
         [(str where " :rows: must be a positive integer")])
       (cond
         (not (and (vector? stop-ids) (seq stop-ids)))
         [(str where " :stop-ids: must be a non-empty vector")]

         (not (every? #(and (string? %) (re-matches stop-id-pattern %)) stop-ids))
         [(str where " :stop-ids: must be IDs like \"HSL:1230410\"")]

         (not (apply distinct? stop-ids))
         [(str where " :stop-ids: contains duplicates")])
       (stop-keyed-problems where :stop-names (:stop-names col) stop-id-set
                            #(and (string? %) (not (str/blank? %)))
                            "a non-blank string")
       (stop-keyed-problems where :hidden-routes (:hidden-routes col) stop-id-set
                            route-keys? "a list of route keys")
       (stop-keyed-problems where :show-routes (:show-routes col) stop-id-set
                            route-keys? "a list of route keys")))))

(defn- board-problems [slug board]
  (let [where (str "board " (pr-str slug))]
    (if-not (map? board)
      [(str where ": must be a map")]
      (let [{:keys [title number-of-departures columns]} board]
        (concat
         (when-not (and (string? slug) (re-matches slug-pattern slug))
           [(str where ": slug must be letters, digits, - or _")])
         (unknown-keys where board board-keys)
         (when-not (and (string? title) (not (str/blank? title)))
           [(str where " :title: must be a non-blank string")])
         (when-not (pos-int? number-of-departures)
           [(str where " :number-of-departures: must be a positive integer")])
         (if-not (and (map? columns) (= #{:left :right} (set (keys columns))))
           [(str where " :columns: must be a map with exactly :left and :right")]
           (concat (column-problems (str where " :left") (:left columns))
                   (column-problems (str where " :right") (:right columns)))))))))

(defn problems
  "A seq of human-readable problems with `boards`; empty when it is valid."
  [boards]
  (if-not (and (map? boards) (seq boards))
    ["boards: must be a non-empty map of slug to board"]
    (mapcat (fn [[slug board]] (board-problems slug board)) boards)))

(defn validate!
  "Return `boards`, or throw listing every problem found."
  [boards]
  (when-let [ps (seq (problems boards))]
    (throw (ex-info (str "Invalid board config:\n  " (str/join "\n  " ps))
                    {:problems (vec ps)})))
  boards)
