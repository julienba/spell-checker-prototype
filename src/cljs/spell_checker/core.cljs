(ns spell-checker.core
  (:require [re-frame.core :as rf]
            [spell-checker.events :as events]
            [spell-checker.views :as views]
            [uix.core :as uix :refer [$]]
            [uix.dom]))

(defonce root (uix.dom/create-root (js/document.getElementById "app")))

(defn mount-root []
  (uix.dom/render-root ($ views/app) root))

(defn ^:dev/after-load reload []
  (rf/clear-subscription-cache!)
  (mount-root))

(defn init []
  (rf/dispatch-sync [::events/initialize-db])
  (mount-root))

