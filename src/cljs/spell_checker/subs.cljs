(ns spell-checker.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 ::slide-content
 (fn [db _]
   (:slide-content db)))

(rf/reg-sub
 ::speaker-notes
 (fn [db _]
   (:speaker-notes db)))

(rf/reg-sub
 ::loading?
 (fn [db _]
   (:loading? db)))

(rf/reg-sub
 ::suggestion
 (fn [db _]
   (:suggestion db)))
