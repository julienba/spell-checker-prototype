(ns spell-checker.suggestions-views
  (:require [re-frame.core :as rf]
            [spell-checker.events :as events]
            [spell-checker.subs :as subs]
            [uix.core :as uix :refer [$ defui]]
            [uix.dom]
            [uix.re-frame :as uix.rf]))

(defui text-with-suggestions [{:keys [suggestions]}]
  ($ :p {:style {:color "#6c757d"
                 :font-style "italic"}}
     (if suggestions
       (for [[idx sug] (map-indexed vector suggestions)]
         (if (map? sug)
           ($ :span {:key idx
                     :style {:background "#f1f3f4"
                             :border-radius "3px"
                             :margin "0 2px"
                             :padding "0 2px"
                             :text-decoration "underline"
                             :text-decoration-color "#e53935"
                             :text-decoration-thickness "2px"
                             :text-underline-offset "2px"}}
              (:origin sug))
           ($ :span {:key idx} (str sug))))
       "Corrected text with suggestions will appear here...")))

(def block-style
  {:border "none"
   :border-radius "3px"
   :color "white"
   :cursor "pointer"
   :padding "0.2rem 0.7rem"})

(defui approve-block [{:keys [idx suggestion]}]
  ($ :div {:style {:display "flex"
                   :flex-direction "column"
                   :gap "10px"}}
     ($ :div {:style {:flex "1"}}
        ($ :span (:origin suggestion))
        ($ :b {:style {:color "black"}} " → ")
        ($ :span (:sugg suggestion)))
     ($ :div {:style {:display "flex"
                      :justify-content "space-between"}}
        ($ :button
           {:on-click #(rf/dispatch [::events/accept-suggestion idx false])
            :style (merge block-style {:background-color "#e53935"})}
           "Ignore")
        ($ :button
           {:on-click #(rf/dispatch [::events/accept-suggestion idx true])
            :style (merge block-style {:background-color "#4caf50"})}
           "Accept"))))

(defui approve-list [{:keys [suggestion-list]}]
  (if (seq suggestion-list)
    ($ :ul
       (for [[idx s] (map-indexed vector suggestion-list)]
         ($ :li {:key idx
                 :style {:display "flex"
                         :align-items "center"
                         :gap "0.5rem"
                         :margin-bottom "0.5rem"}}
            ($ approve-block {:idx idx :suggestion s}))))
    "Approve/Refuse buttons will appear here..."))

(defui suggestions-display []
  (let [suggestion (uix.rf/use-subscribe [::subs/suggestion])
        suggestion-cnt (count (filter map? (-> suggestion :value :suggestions)))
        display-suggestion? (and suggestion
                                 (= "ok" (:status suggestion))
                                 (pos-int? suggestion-cnt))]
    (prn ::suggestion suggestion)
    (when-not (nil? suggestion)
      ($ :div {:style {:margin-top "1.5rem"
                       :padding "1rem"
                       :background-color "#f8f9fa"
                       :border "1px solid #dee2e6"
                       :border-radius "4px"
                       :min-height "200px"}}
         (if (= "error" (:status suggestion))
           ($ :div
              ($ :h3 "Oops an error occured")
              ($ :span (-> suggestion :value :msg)))
           ($ :h3 {:style {:margin-top 0
                           :margin-bottom "1rem"
                           :font-size "18px"
                           :color "#333"}}
              (cond
                (nil? suggestion)
                "No suggestions (yet)"

                (and suggestion (pos-int? suggestion-cnt))
                (str suggestion-cnt " suggestions (in " (-> suggestion :value :request-time) "ms)")

                :else "No more suggestions")))
         (when display-suggestion?
           ($ :div {:style {:display "flex"
                            :gap "1rem"}}
              ($ :div {:style {:flex "3"
                               :padding "1rem"
                               :background-color "white"
                               :border "1px solid #dee2e6"
                               :border-radius "4px"
                               :min-height "150px"}}
                 ($ text-with-suggestions {:suggestions (-> suggestion :value :suggestions)}))
              ($ :div {:style {:flex "1"
                               :padding "1rem"
                               :background-color "white"
                               :border "1px solid #dee2e6"
                               :border-radius "4px"
                               :min-height "150px"}}
                 ($ :div {:style {:color "#6c757d"
                                  :font-style "italic"
                                  :font-size "14px"}}
                    ($ approve-list {:suggestion-list (filter map? (-> suggestion :value :suggestions))})))))))))
