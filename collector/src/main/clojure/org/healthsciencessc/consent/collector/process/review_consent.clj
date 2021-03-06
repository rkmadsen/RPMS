(ns org.healthsciencessc.consent.collector.process.review-consent
  (:refer-clojure :exclude [root])
  (:require [org.healthsciencessc.consent.collector.common :as common]
            [org.healthsciencessc.consent.collector.lock :as locker]
            [org.healthsciencessc.consent.collector.respond :as respond]
            [org.healthsciencessc.consent.collector.state :as state]
            [org.healthsciencessc.consent.collector.text :as text]
            [org.healthsciencessc.consent.collector.process.authorize :as auth]
            [org.healthsciencessc.consent.collector.ui.layout :as layout]
            [org.healthsciencessc.consent.collector.ui.content :as uicontent]
            [org.healthsciencessc.consent.client.core :as services]
            [pliant.webpoint.request :as endpoint])
  (:use     [pliant.process :only [defprocess as-method]]
            [clojure.string :only [join]]))


;; Register The Review Consent View
(defprocess view-review-consent
  "Creates a view for reviewing metaitmes"
  [ctx]
  (if (auth/is-authenticated?)
    (let [data {}
          location-name (:name (state/get-location))
          consenter-name (common/formal-name (state/get-consenter))
          encounter-id (:encounter-id (state/get-encounter))]
      (layout/render-page ctx {:title (text/consenter-text :review.consent.title) :pageid "ReviewConsent"
                          :header-left "" :header-right ""
                          :uigenerator "review" :uigenerator-data {:data-submit-url "/api/review/consent"
                                                                    :data-submit-method "POST"
                                                                    :data-back-label (text/text :review.consent.back.label)
                                                                    :data-next-label (text/text :review.consent.next.label)}}))
    (respond/forbidden-view ctx)))

(as-method view-review-consent endpoint/endpoints "get-view-review-consent")

;; Register The Collect Consent Service
(defprocess api-review-consent
  "Processes the values reviewed by the consent process and forwards on to the review process."
  [ctx]
  (if (auth/is-authenticated?)
    (let [body (:body-params ctx)]
      (respond/with-actions {:view-url "/view/witness/signatures" :reset false} "changeView"))
    (respond/forbidden-view ctx)))

(as-method api-review-consent endpoint/endpoints "post-api-review-consent")

