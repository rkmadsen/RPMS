(ns org.healthsciencessc.clj.war.bootstrap
  (:require [org.healthsciencessc.rpms2.consent-admin.core :as core]))

;; Bootrap a pointer to the real routes.
(def app core/app)

(defn init
  "Provides a function placeholder to initialize the application from."
  [event]
  (println "Initializing the Consent Admin Application."))

(defn destroy
  "Provides a function placeholder to perform destroy operations whent the servlet is shutdown."
  [event]
  (println "Destroying the Consent Admin Application."))