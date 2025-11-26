(ns spell-checker.server
  (:require [ring.adapter.jetty :as jetty]
            [spell-checker.handler :as handler]))

(defonce server (atom nil))

(defn start-server [port]
  (when @server
    (.stop @server))
  (reset! server (jetty/run-jetty #'handler/cors-app
                                   {:port port
                                    :join? false}))
  (println (str "Server started on port " port)))

(comment
  (defn stop-server []
    (when @server
      (.stop @server)
      (reset! server nil)
      (println "Server stopped"))))

