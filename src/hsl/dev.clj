(ns hsl.dev
  "Local dev entrypoint with live reloading (`bb dev`).

   Starts the same server as `bb serve`, but turns Selmer's template cache
   off so edits under views/ show on the next request, and polls src/ and views/
   for changes: a saved .clj re-requires the app onto the running server, so
   code and template changes take effect without a manual restart.

   Not used in production; `bb serve` (hsl.server/-main) stays the plain
   always-on server."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [hsl.server :as server]
            [selmer.parser :as selmer]))

(def ^:private watch-dirs ["src" "views"])
(def ^:private poll-ms 500)

(defn- mtimes
  "Map of every watched source/template file to its last-modified millis."
  [dirs]
  (into {}
        (for [dir dirs
              f (mapcat #(fs/glob dir %) ["**.clj" "**.html"])]
          [(str f) (fs/file-time->millis (fs/last-modified-time f))])))

(defn- reload!
  "Re-require the app so the running server picks up the `changed` files. Only a
   .clj change needs it; templates are re-read on demand. `:reload-all` reaches
   Selmer, so re-assert the cache setting after."
  [changed]
  (server/log! "Reloading:" (str/join ", " (sort changed)))
  (when (some #(str/ends-with? % ".clj") changed)
    (try
      (require 'hsl.server :reload-all)
      (selmer/cache-off!)
      (server/log! "Reloaded")
      (catch Exception e
        (server/log-error! "Reload failed:" (ex-message e))))))

(defn- watch!
  "Poll `dirs` forever, reloading whenever a watched file's mtime changes."
  [dirs]
  (server/log! "Watching" (str/join ", " dirs) "for changes")
  (loop [prev (mtimes dirs)]
    (Thread/sleep poll-ms)
    (let [now (mtimes dirs)
          changed (for [[f t] now :when (not= t (get prev f))] f)]
      (when (seq changed)
        (reload! (set changed)))
      (recur now))))

(defn -main [& _]
  (selmer/cache-off!)
  (let [{:keys [stop]} (server/start!)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable (fn []
                                           (server/log! "Server shutting down")
                                           (stop))))
    (watch! watch-dirs)))
