(ns spell-check.handler-test
  (:require [clojure.test :refer [deftest is testing]]
            [bond.james :as bond]
            [spell-checker.handler :as sut]
            [spell-checker.llm :as llm]))

(deftest health-check-test
  (testing "returns ok status with message"
    (let [response (sut/health-check {})]
      (is (= {:status 200
              :body {:status "ok"
                     :message "Spell checker API is running"}}
             response)))))

(deftest post-spell-check-success-test
  (testing "returns successful spell check result"
    (bond/with-stub! [[llm/run-spell-check (constantly
                                            {:status :ok
                                             :value {:request-time 123
                                                     :suggestions "The `{\"origin\": \"projeckt\", \"sugg\": \"project\"}` failed twice."}})]]
      (let [request {:body-params {:slide-content "Some slide content"
                                   :speaker-notes "The projeckt failed twice."}}
            response (sut/post-spell-check request)]
        (is (= {:status 200
                :body {:status :ok
                       :value {:request-time 123
                               :suggestions "The `{\"origin\": \"projeckt\", \"sugg\": \"project\"}` failed twice."}}}
               response))
        (is (= 1 (count (bond/calls #'llm/run-spell-check))))
        (let [call-args (:args (first (bond/calls #'llm/run-spell-check)))]
          (is (= "Some slide content" (first call-args)))
          (is (= "The projeckt failed twice." (second call-args))))))))

(deftest post-spell-check-error-test
  (testing "returns error spell check result"
    (bond/with-stub! [[llm/run-spell-check (constantly
                                            {:status :error
                                             :value {:msg "Out of retry"}})]]
      (let [request {:body-params {:slide-content "Some slide content"
                                   :speaker-notes "The projeckt failed."}}
            response (sut/post-spell-check request)]
        (is (= {:status 200
                :body {:status :error
                       :value {:msg "Out of retry"}}}
               response))
        (is (= 1 (count (bond/calls #'llm/run-spell-check))))))))

