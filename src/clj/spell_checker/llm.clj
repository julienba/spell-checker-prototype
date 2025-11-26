(ns spell-checker.llm
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clj-sac.llm.http.gemini :as gemini]
            [clj-sac.prompt :as prompt]
            [spell-checker.parser :as parser]))

(def GEMINI-TOKEN (System/getenv "GEMINI_API_KEY"))

(when-not GEMINI-TOKEN
  (log/fatal "'GEMINI_API_KEY' is not set"))

(def prompt-path
  "prompt/spell_check_v2.prompt")

(def spell-check-prompt
  (prompt/load-prompt (io/resource prompt-path)))

(def max-retry 3)

(defn sleep [time-in-ms]
  (Thread/sleep time-in-ms))

(defn- calculate-jitter
  "Adds random jitter to increase the retry success"
  [delay-ms]
  (let [factor (+ 1.0 (* 0.2 (rand)))]
    (long (* delay-ms factor))))

(defn- run-spell-check'
  "Call Gemini and wrap its answer in a map."
  [rendered-prompt]
  (loop [attempt 0]
    (if (= attempt max-retry)
      {:status :error
       :value {:msg "Out of retry"}}
      (let [{:keys [body request-time status] :as response} (gemini/chat-completion
                                                             {:messages [{:content rendered-prompt :role "user"}]
                                                              ;; "gemini-2.5-flash-lite" give errors when test on more elaborate data
                                                              :model "gemini-2.5-flash"}
                                                             {:headers {"x-goog-api-key" GEMINI-TOKEN}})]
        ;(tap> [:llm-response response]) ; debug
        (cond
          (and (= 200 status)
               ; For this prototype I assume that the full text will be contain in the first element.
               ; No server side event either.
               (= "STOP" (get-in body [:candidates 0 :finishReason])))
          (do
            (log/info "Gemini usage: " (:usageMetadata body))
            {:status :ok
             :value {:request-time request-time
                     :suggestions (get-in body [:candidates 0 :content :parts 0 :text])}})

          (= 200 status)
          {:status :error
           :value {:msg "Unexpected body (No STOP)"
                   :request-time request-time}}

          (= 429 status)
          (do
            ; https://docs.cloud.google.com/vertex-ai/generative-ai/docs/provisioned-throughput/error-code-429
            (log/error "Quota exhausted or too many requests")
            (sleep (calculate-jitter (* 1000 (inc attempt))))
            (recur (inc attempt)))

          :else
          (throw
           (ex-info "Unexpected response"
                    (dissoc response :http-client))))))))

(defn eval-llm-output
  "Evaluate the LLM output.
   Return a vector with the evaluation result as first element the reason as second element."
  [original-text suggest-text]
  (if (string/blank? suggest-text)
    [:error "No text"]
    (let [tokens (parser/tokenize-string suggest-text)]
      (cond
        (not (parser/validate-tokenize-output? tokens))
        [false "Invalid tokens"]

        (false? (= original-text (parser/tokens->str tokens)))
        [false "The origin texts have been altered"]

        (and (= original-text (parser/apply-all tokens))
             (zero? (count (filter map? tokens))))
        [true "The suggestions are identical"]

        (= original-text (parser/apply-all tokens))
        [false "The suggestions are identical"]

        :else [true]))))

(defn run-spell-check
  "Runs spell checking using an LLM with a naive retry strategy.

   Returns a map:
     {:status :ok
      :value {:request-time <ms>
              :suggestions <string>}}
   Or, on error:
     {:status :error
      :value {:msg <string>}}"
  [slide-content notes]
  (let [render-prompt (:render spell-check-prompt)
        rendered (render-prompt {:context slide-content
                                 :notes notes})
        response (run-spell-check' rendered)
        [eval? eval-msg] (when (= :ok (:status response))
                           (eval-llm-output notes (-> response :value :suggestions)))]
    ;; If the llm response is ok, run the eval
    ;; else return the (error) response
    (if (= :ok (:status response))
      (if eval?
        response
        (do
          (log/warn "Evals are failing" {:eval-msg eval-msg
                                         :notes notes
                                         :suggestions (-> response :value :suggestions)})
          {:status :error
           :value {:msg (str "Evals are failing: " eval-msg)}}))
      response)))

(comment
  (time (run-spell-check "What you see is what you get"
                         "The kat is in the kitchen"))


  (time (run-spell-check "What you see is what you get"
                         "The projeckt failed."))

  ; With gemini-2.5-flash it works but not with gemini-2.5-flash-lite
  (time (run-spell-check "Title: The projeckt \n\n The projeckt is a dutch company working on the retail sector."
                         "The projeckt about the self servoce failed."))

  (time (run-spell-check "HEADER: Brand: LeanNews Date: 11 November 2025\n\n
                          Title: Reclaim your time with LeanNews"
                         "A quick self introduction follow by the LeanMews elevator pitch"))

  ; In practice this one convert LeanNews into LeanMews silently using  gemini-2.5-flash-lite.
  ; With gemini-2.5-flash it works but take 4 times longer.
  (time (run-spell-check "HEADER: Brand: LeanMews Date: 11 November 2025\n\n
                          Title: Reclaim your time with LeanMews"
                         "A quick self introduction follow by the LeanNews elevator pitch")))
