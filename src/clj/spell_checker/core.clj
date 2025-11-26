(ns spell-checker.core
  (:require [spell-checker.server :as server])
  (:gen-class))

(defn -main [& _args]
  (let [port (or (some-> (System/getenv "PORT") Integer/parseInt) 3000)]
    (server/start-server port)
    (println (str "Spell checker application running at http://localhost:" port))))
