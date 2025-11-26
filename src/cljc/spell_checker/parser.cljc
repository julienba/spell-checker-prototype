(ns spell-checker.parser
  "Utility to parse and modify the LLM output."
  (:require [clojure.string :as string]
            #?(:clj [clojure.data.json :as json])))

(def backtick-pattern
  #"`([^`]+)`")

#?(:clj (defn- parse-json-content [json-string]
          (try
            (json/read-str json-string :key-fn keyword)
            (catch Exception _
              nil))))

#?(:cljs (defn- parse-json-content [json-string]
           (try
             (js->clj (.parse js/JSON json-string) :keywordize-keys true)
             (catch js/Error _e
               nil))))

(defn tokenize-string
  "Splits the string into a vector of text and parsed maps.
   Result format: [\"The \" {:origin \"...\"} \" failed.\"]"
  [input-str]
  (let [matches (re-seq backtick-pattern input-str)]
    (if (empty? matches)
      [input-str]
      (let [{:keys [result last-idx]}
            (reduce
             (fn [{:keys [result last-idx]} [full-match json-content]]
               (let [match-start (string/index-of input-str full-match last-idx)
                     match-end (+ match-start (count full-match))
                     pre-text (subs input-str last-idx match-start)
                     parsed-obj (parse-json-content json-content)]
                 (if parsed-obj
                   {:result (conj result pre-text parsed-obj)
                    :last-idx match-end}
                   {:result (conj result pre-text)
                    :last-idx match-end})))
             {:result [] :last-idx 0}
             matches)
            remaining (when (< last-idx (count input-str))
                        (subs input-str last-idx))]
        (if remaining
          (conj result remaining)
          result)))))

(defn validate-tokenize-output? [xs]
  (boolean
   (and (seq xs)
        (every? (fn [item]
                  (or (string? item)
                      (and (map? item)
                           (contains? item :origin)
                           (contains? item :sugg))))
                xs))))

(defn update-tokenize-output [tokens position accept?]
  (loop [xs tokens
         results []
         cnt 0]
    (if (empty? xs)
      results
      (let [e (first xs)
            [new-e new-cnt] (if (map? e)
                              (if (= position cnt)
                                [(if accept? (:sugg e) (:origin e)) cnt]
                                [e (inc cnt)])
                              [e cnt])]
        (if (= e new-e)
          (recur (rest xs) (conj results new-e) new-cnt)
          (concat (conj results new-e) (rest xs)))))))

(defn tokens->str [tokens]
  (->> tokens
       (map #(if (map? %) (:origin %) %))
       (apply str)))

(defn apply-all
  "Apply all the sugestions"
  [tokens]
  (let [cnt (count (filter map? tokens))
        applied-tokens (reduce
                        (fn [acc _i]
                          (update-tokenize-output acc 0 true))
                        tokens
                        (range 0 cnt))]
    (tokens->str applied-tokens)))
