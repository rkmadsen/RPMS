(ns org.healthsciencessc.rpms2.consent-services.core
  (:use compojure.core
        ring.adapter.jetty
        [clojure.string :only (blank? join split)])
  (:require [compojure.handler :as handler]
            [org.healthsciencessc.rpms2.consent-services.process-dispatcher :as dispatcher]
            [org.healthsciencessc.rpms2.consent-services.process :as process]))

(defn uri->process-name
  [method uri]
  (let [split-uri (filter #(not (blank? %)) (split uri #"\/"))]
    (join "-" (conj split-uri method))))

(defroutes service-routes
  (ANY "*" {uri :uri method :request-method params :query-params}
       (let [process-name (uri->process-name (name method) uri)
             result (dispatcher/dispatch process-name params)]
         (if (nil? result)
             {:status 404 :body "Process not found"}
             result))))

(def ^:private app
  (-> service-routes
      handler/api))
