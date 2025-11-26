(ns spell-check.llm-test
  (:require [clojure.test :refer [deftest is testing]]
            [bond.james :as bond]
            [spell-checker.llm :as sut]
            [spell-checker.parser :as parser]
            [clj-sac.llm.http.gemini :as gemini]))

(def retry-response
  {:status 429
   :body {}
   :request-time 100})

(def eval-stub
  (constantly
   [true]))

(deftest retry-on-429-success-after-retry-test
  (testing "Should retry on 429 status and succeed after retry"
    (let [call-count (atom 0)
          stub-fn (fn [& _]
                    (swap! call-count inc)
                    (if (= 1 @call-count)
                      retry-response
                      {:status 200
                       :body {:candidates [{:finishReason "STOP"
                                            :content {:parts [{:text "test prompt"}]}}]
                              :usageMetadata {}}
                       :request-time 200}))]
      (bond/with-stub! [[gemini/chat-completion stub-fn]
                        [sut/sleep (constantly nil)]
                        [sut/eval-llm-output eval-stub]]
        (is (= {:status :ok
                :value {:request-time 200
                        :suggestions "test prompt"}}
               (sut/run-spell-check "" "test prompt")))
        (is (= 2 @call-count))))))

(deftest retry-exhausted-test
  (testing "Should return error after max-retry attempts"
    (bond/with-stub! [[gemini/chat-completion (constantly retry-response)]
                      [sut/sleep (constantly nil)]]
      (is (= {:status :error
              :value {:msg "Out of retry"}}
             (sut/run-spell-check "text context" "test prompt")))
      (is (= sut/max-retry (count (bond/calls #'gemini/chat-completion)))))))

(deftest no-retry-on-success-test
  (testing "Should not retry on successful response"
    (bond/with-stub! [[gemini/chat-completion (constantly
                                               {:status 200
                                                :body {:candidates [{:finishReason "STOP"
                                                                     :content {:parts [{:text "test prompt"}]}}]
                                                       :usageMetadata {}}
                                                :request-time 150})]
                      [sut/sleep (constantly nil)]]
      (let [result (sut/run-spell-check "" "test prompt")]
        (is (= :ok (:status result)))
        (is (= 1 (count (bond/calls #'gemini/chat-completion))))))))

(deftest no-retry-on-200-without-stop-test
  (testing "Should not retry on 200 status without STOP finishReason"
    (bond/with-stub! [[gemini/chat-completion (constantly
                                               {:status 200
                                                :body {:candidates [{:finishReason "MAX_TOKENS"
                                                                     :content {:parts [{:text "suggestions"}]}}]}
                                                :request-time 150})]
                      [sut/sleep (constantly nil)]
                      [sut/eval-llm-output eval-stub]]
      (is (= {:status :error,
              :value {:msg "Unexpected body (No STOP)", :request-time 150}}
             (#'sut/run-spell-check' "test prompt")))
      (is (= 1 (count (bond/calls #'gemini/chat-completion))))
      (is (zero? (count (bond/calls #'sut/eval-llm-output)))))))

(deftest no-retry-on-other-errors-test
  (testing "Should throw exception on non-429 errors without retrying"
    (bond/with-stub! [[gemini/chat-completion (constantly {:status 500
                                                           :body {}
                                                           :request-time 100})]]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Unexpected response"
                            (sut/run-spell-check "" "test prompt")))
      (is (= 1 (count (bond/calls #'gemini/chat-completion)))))))

(deftest eval-llm-answer-test
  (is (= [:error "No text"]
         (#'sut/eval-llm-output "" "")
         (#'sut/eval-llm-output "text" "")
         (#'sut/eval-llm-output "text" "   "))
      "returns error when suggest-text is blank")

  (testing "returns error when tokens are invalid"
    (with-redefs [parser/tokenize-string (constantly [])]
      (is (= [false "Invalid tokens"]
             (#'sut/eval-llm-output "text" "some text")))))

  (testing "returns error when original text has been altered"
    (is (= [false "The origin texts have been altered"]
           (#'sut/eval-llm-output "text" "texts")))
    (is (= [false "The origin texts have been altered"]
           (#'sut/eval-llm-output "The projeckt failed."
                                  "The `{\"origin\": \"projeckt\", \"sugg\": \"project\"}` failed. Extra text"))))

  (is (= [true "The suggestions are identical"]
         (#'sut/eval-llm-output "text" "text"))
      "No suggestions")

  (is (= [false "The suggestions are identical"]
         (#'sut/eval-llm-output "The projeckt failed."
                                "The `{\"origin\": \"projeckt\", \"sugg\": \"projeckt\"}` failed."))
      "returns error when suggestions are identical to original")

  (testing "returns ok when suggestions are valid and different"
    (is (= [true]
           (#'sut/eval-llm-output "The projeckt failed twice."
                                  "The `{\"origin\": \"projeckt\", \"sugg\": \"project\"}` failed twice.")))
    (is (= [true]
           (#'sut/eval-llm-output "The projeckt failed."
                                  "The `{\"origin\": \"projeckt\", \"sugg\": \"project\"}` failed.")))))

(deftest success-call-and-eval-test
  (bond/with-stub! [[gemini/chat-completion (constantly
                                             {:status 200
                                              :body {:candidates [{:finishReason "STOP"
                                                                   :content {:parts [{:text "test prompt"}]}}]
                                                     :usageMetadata {}}
                                              :request-time 150})]
                    [sut/sleep (constantly nil)]]
    (let [result (sut/run-spell-check "" "test prompt")]
      (is (= :ok (:status result)))
      (is (= 1 (count (bond/calls #'gemini/chat-completion)))))))

(deftest success-call-with-fail-eval
  (bond/with-stub! [[gemini/chat-completion (constantly
                                             {:status 200
                                              :body {:candidates [{:finishReason "STOP"
                                                                   :content {:parts [{:text "yolo"}]}}]
                                                     :usageMetadata {}}
                                              :request-time 150})]
                    [sut/sleep (constantly nil)]]
    (is (= {:status :error
            :value {:msg "Evals are failing: The origin texts have been altered"}}
           (sut/run-spell-check "" "test prompt")))))
