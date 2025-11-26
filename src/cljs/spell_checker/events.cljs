(ns spell-checker.events
  (:require [re-frame.core :as rf]
            [spell-checker.db :as db]
            [spell-checker.parser :as parser]
            [superstructor.re-frame.fetch-fx]))

(rf/reg-event-db
 ::initialize-db
 (fn [_ _]
   db/starting-db))

(rf/reg-event-db
 ::set-slide-content
 (fn [db [_ text]]
   (assoc db :slide-content text)))

(rf/reg-event-db
 ::set-speaker-notes
 (fn [db [_ text]]
   (assoc db :speaker-notes text)))

(rf/reg-event-fx
 ::spell-check-result
 (fn [{:keys [db]} [_ {:keys [body]}]]
   {:db (assoc db
               :loading? false
               :suggestion (cond-> body
                             (= "ok" (:status body))
                             (update-in [:value :suggestions] parser/tokenize-string)))}))

(rf/reg-event-fx
 ::spell-check-error
 (fn [{:keys [db]} [_ resp]]
   (prn "Oops the call fail: " resp)
   {:db (assoc db :loading? false)}))

(rf/reg-event-fx
 ::check-spelling
 (fn [{:keys [db]} _]
   {:db (assoc db :loading? true)
    :fetch {:method                 :post
            :url                    "/api/spell-check"
            :request-content-type :json
            :body                   {:speaker-notes (:speaker-notes db)
                                     :slide-content (:slide-content db)}
            :mode                   :cors
            :timeout                10000
            :response-content-types {#"application/.*json" :json}
            :on-success             [::spell-check-result]
            :on-failure             [::spell-check-error]}}))

(rf/reg-event-db
 ::accept-suggestion
 (fn [db [_ idx accept?]]
   (let [old-suggestions (-> db :suggestion :value :suggestions)
         new-suggestions (parser/update-tokenize-output old-suggestions idx accept?)]
     (-> db
         (assoc :speaker-notes (parser/tokens->str new-suggestions))
         (assoc-in [:suggestion :value :suggestions] new-suggestions)))))