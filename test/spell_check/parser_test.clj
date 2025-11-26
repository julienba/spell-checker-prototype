(ns spell-check.parser-test
  (:require [clojure.test :refer [is deftest testing]]
            [spell-checker.parser :as sut]))

(deftest test-tokenize-string
  (testing "tokenizes string with single backtick-enclosed JSON"
    (is (= (sut/tokenize-string "The `{\"origin\": \"projeckt\", \"sugg\": \"project\"}` failed.")
           ["The " {:origin "projeckt", :sugg "project"} " failed."])))

  (testing "tokenizes string with multiple backtick-enclosed JSON"
    (is (= ["The " {:origin "projeckt", :sugg "project"} " failed. The " {:origin "projeckt", :sugg "project"} " failed."]
           (sut/tokenize-string "The `{\"origin\": \"projeckt\", \"sugg\": \"project\"}` failed. The `{\"origin\": \"projeckt\", \"sugg\": \"project\"}` failed."))))

  (testing "tokenizes string without backticks"
    (is (= (sut/tokenize-string "The yolo failed.") ["The yolo failed."])))

  (testing "handles invalid JSON in backticks"
    (let [result (sut/tokenize-string "The `invalid json` failed.")]
      (is (some string? result))
      (is (not-any? map? result)))))

(deftest test-validate-tokenize-output?
  (testing "validates correct tokenize output"
    (is (sut/validate-tokenize-output?
         (sut/tokenize-string "The `{\"origin\": \"projeckt\", \"sugg\": \"project\"}` failed."))))

  (testing "rejects empty sequence"
    (is (not (sut/validate-tokenize-output? []))))

  (testing "rejects invalid map structure"
    (is (not (sut/validate-tokenize-output?
              [{:origin "projeckt", :suggTEST "project"}]))))

  (testing "accepts valid mixed tokens"
    (is (sut/validate-tokenize-output?
         ["The " {:origin "projeckt", :sugg "project"} " failed."]))))

(deftest test-update-tokenize-output
  (let [tokens (sut/tokenize-string "The `{\"origin\": \"projeckt\", \"sugg\": \"project\"}` failed.")]
    (testing "accepts suggestion at position 0"
      (is (= (sut/update-tokenize-output tokens 0 true)
             ["The " "project" " failed."])))

    (testing "rejects suggestion at position 0"
      (is (= (sut/update-tokenize-output tokens 0 false)
             ["The " "projeckt" " failed."])))

    (testing "does not update when position doesn't match"
      (is (= (sut/update-tokenize-output tokens 1 false)
             tokens)))

    (testing "does not update when position is out of bounds"
      (is (= (sut/update-tokenize-output tokens 10 false)
             tokens)))))

(deftest test-tokens->str
  (testing "converts tokens to string"
    (is (= (sut/tokens->str ["The " {:origin "projeckt", :sugg "project"} " failed."])
           "The projeckt failed.")))

  (testing "handles string-only tokens"
    (is (= (sut/tokens->str ["The " "yolo" " failed."])
           "The yolo failed.")))

  (testing "handles empty tokens"
    (is (= (sut/tokens->str [])
           ""))))

(deftest test-apply-all
  (testing "applies single suggestion"
    (is (= (sut/apply-all ["The " {:origin "projeckt", :sugg "project"} " failed."])
           "The project failed.")))

  (testing "applies multiple suggestions"
    (is (= (sut/apply-all ["The " {:origin "projeckt", :sugg "project"} " failed. The " {:origin "eror", :sugg "error"} " occurred."])
           "The project failed. The error occurred.")))

  (testing "handles tokens with no suggestions"
    (is (= (sut/apply-all ["The " "yolo" " failed."])
           "The yolo failed.")))

  (testing "handles empty tokens"
    (is (= (sut/apply-all [])
           ""))))
