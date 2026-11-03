(ns hsl.config
  "Board config: `boards` maps a URL slug to a board definition, read from
   boards.edn in the working directory, or from the committed
   boards.example.edn when there is none. See boards.example.edn for the format."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]))

(def boards-file "boards.edn")
(def example-file "boards.example.edn")

(defn read-boards
  "Read the boards map from the EDN file at `path`, naming the file in any
   parse error."
  [path]
  (try
    (edn/read-string (slurp path))
    (catch Exception e
      (throw (ex-info (str path ": " (ex-message e)) {:file path} e)))))

(defn boards-path
  "The board config in use: boards.edn if present, else the example."
  []
  (if (fs/exists? boards-file) boards-file example-file))

(def boards (read-boards (boards-path)))

(defn stop-ids
  "All stop IDs across both columns, in board order."
  [board]
  (into (get-in board [:columns :left :stop-ids])
        (get-in board [:columns :right :stop-ids])))
