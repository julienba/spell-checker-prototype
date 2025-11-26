(ns spell-checker.views
  (:require [re-frame.core :as rf]
            [spell-checker.events :as events]
            [spell-checker.subs :as subs]
            [spell-checker.suggestions-views :as suggestions-views]
            [uix.core :as uix :refer [$ defui]]
            [uix.dom]
            [uix.re-frame :as uix.rf]))

(def label-style
  {:display "block"
   :margin-bottom "0.5rem"
   :font-weight "600"
   :color "#333"})

(def textarea-style
  {:padding "0.75rem"
   :border "1px solid #ccc"
   :border-radius "4px"
   :font-family "inherit"
   :font-size "14px"
   :resize "vertical"})

(defui slide-content-input []
  (let [slide-content (uix.rf/use-subscribe [::subs/slide-content])]
    ($ :div {:style {:margin-bottom "1.5rem"}}
       ($ :label {:style label-style}
          "Slide Content")
       ($ :textarea {:value slide-content
                     :on-change #(rf/dispatch [::events/set-slide-content (.. % -target -value)])
                     :rows 10
                     :placeholder "Enter the content of your slide here..."
                     :style (merge textarea-style
                                   {:width "100%"})}))))

(defui speaker-notes-section []
  (let [speaker-notes (uix.rf/use-subscribe [::subs/speaker-notes])]
    ($ :div {:style {:margin-bottom "1.5rem"}}
       ($ :label {:style label-style}
          "Speaker Notes")
       ($ :div {:style {:display "flex"
                        :gap "1rem"
                        :align-items "flex-start"}}
          ($ :textarea {:value speaker-notes
                        :on-change #(rf/dispatch [::events/set-speaker-notes (.. % -target -value)])
                        :rows 10
                        :placeholder "Enter your speaker notes here..."
                        :style (merge textarea-style
                                      {:flex "1"})})))))

(defui actions []
  (let [loading (uix.rf/use-subscribe [::subs/loading?])]
    ($ :div {:style {:margin-bottom "1.5rem"}}
       ($ :button {:on-click #(rf/dispatch [::events/check-spelling])
                   :disabled loading
                   :style {:padding "0.75rem 1.5rem"
                           :background-color "#007bff"
                           :color "white"
                           :border "none"
                           :border-radius "4px"
                           :cursor (if loading "not-allowed" "pointer")
                           :opacity (if loading 0.6 1)
                           :font-weight "600"
                           :white-space "nowrap"
                           :height "fit-content"}}
          (if loading "Checking..." "Check Spelling")))))

(def explanatory-data
  [{:title "Enter Slide Content"
    :content "Type or paste the content of your slide into the first text area. This will be used by the spell checker for context."}
   {:title "Add Speaker Notes"
    :content "Enter your speaker notes in the second text area. These will be spell checked."}
   {:title "Check Spelling"
    :content "Click the 'Check Spelling' button to start the speaker notes checking.
              In a real application, the call could be automatically made using debounce."}
   {:title "Review Suggestions"
    :content "The corrected text will appear in the suggestions area below. You can approve or reject individual suggestions using the buttons on the right."}])

(defui explanatory-text []
  ($ :div {:style {:padding "1.5rem"
                   :background-color "#f8f9fa"
                   :border-radius "4px"
                   :border "1px solid #dee2e6"}}
     ($ :h2 {:style {:margin-top 0
                     :margin-bottom "1rem"
                     :font-size "20px"
                     :color "#333"}}
        "How to use")
     ($ :div {:style {:line-height "1.6"
                      :color "#555"}}
        (for [[idx {:keys [title content]}] (map-indexed vector explanatory-data)]
          ($ :div {:key idx}
             ($ :h3 {:style {:font-size "16px"
                             :margin-top "1rem"
                             :margin-bottom "0.5rem"
                             :color "#333"}}
                (str (inc idx) ". " title))
             ($ :p {:style {:margin-bottom "1rem"}}
                content))))))

(def app-container-style
  {:display "flex"
   :gap "2rem"
   :max-width "1800px"
   :margin "0 auto"
   :padding "2rem"
   :font-family "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif"})

(defui app []
  ($ :div {:style app-container-style}
     ($ :div {:style {:flex "0 0 60%"}}
        ($ :h1 {:style {:color "#333"
                        :margin-bottom "2rem"
                        :margin-top 0}}
           "Slide Deck Spell Checker")
        ($ slide-content-input)
        ($ speaker-notes-section)
        ($ actions)
        ($ suggestions-views/suggestions-display))
     ($ :div {:style {:flex "0 0 38%"}}
        ($ explanatory-text))))
