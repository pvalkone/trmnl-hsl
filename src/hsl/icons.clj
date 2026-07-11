(ns hsl.icons
  "Monochrome SVG icons embedded as inline `data:` URIs, so the board data and
   the templates can reference them with no external fetch at render time."
  (:require [clojure.java.io :as io])
  (:import (java.util Base64)))

(defn- svg-data-uri
  "Base64 `data:` URI for the classpath SVG at `resource-path`."
  [resource-path]
  (str "data:image/svg+xml;base64,"
       (.encodeToString (Base64/getEncoder)
                        (.getBytes ^String (slurp (io/resource resource-path)) "UTF-8"))))

(def mode-icons
  "Maps a vehicleMode to an inline data-URI for a monochrome mode icon."
  {"BUS"    (svg-data-uri "icons/bus.svg")
   "TRAM"   (svg-data-uri "icons/tram.svg")
   "SUBWAY" (svg-data-uri "icons/subway.svg")
   "RAIL"   (svg-data-uri "icons/train.svg")
   "FERRY"  (svg-data-uri "icons/ferry.svg")})

(def alerts-icon
  "Inline data-URI for the service alerts heading icon."
  (svg-data-uri "icons/alert.svg"))
