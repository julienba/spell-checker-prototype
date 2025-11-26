(ns spell-checker.handler
  (:require [muuntaja.core :as m]
            [reitit.ring :as ring]
            [reitit.coercion.spec]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.response :as response]
            [spell-checker.llm :as llm]))

(defn health-check [_request]
  {:status 200
   :body {:status "ok"
          :message "Spell checker API is running"}})

;; For testing the UI without spending token
(def test-response
  {:ok  {:status 200
         :body {:status :ok
                :value {:request-time 123
                        :suggestions "The `{\"origin\": \"projeckt\", \"sugg\": \"project\"}` failed twice."}}}
   :error {:status 200
           :body {:status :error
                  :value {:msg "Out of retry"}}}})

(defn post-spell-check [{:keys [body-params] :as _request}]
  (let [{:keys [slide-content speaker-notes]} body-params]
    {:status 200
     :body (llm/run-spell-check slide-content speaker-notes)}))

(def routes
  [["/api"
    ["/health" {:get health-check}]
    ["/spell-check" {:post post-spell-check}]]])

(defn spa-handler [_request]
  (response/resource-response "index.html" {:root "public"}))

(def app
  (ring/ring-handler
   (ring/router routes
                {:data {:muuntaja m/instance
                        :middleware [parameters/parameters-middleware
                                     muuntaja/format-middleware
                                     exception/exception-middleware
                                     rrc/coerce-request-middleware
                                     rrc/coerce-response-middleware]}})
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler {:not-found spa-handler}))))

(def cors-app
  (wrap-cors app
             :access-control-allow-origin [#".*"]
             :access-control-allow-methods [:get :post :put :delete :options]
             :access-control-allow-headers ["Content-Type" "Authorization"]))

