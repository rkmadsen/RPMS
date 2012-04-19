(ns org.healthsciencessc.rpms2.consent-services.default-processes.role
  (:use [clojure.data.json :only (json-str pprint-json)]
        [org.healthsciencessc.rpms2.consent-services.domain-utils :only (admin? super-admin? some-kind-of-admin? forbidden-fn)])
  (:require [org.healthsciencessc.rpms2.process-engine.core :as process]
            [org.healthsciencessc.rpms2.consent-services.data :as data])
  (:import [org.healthsciencessc.rpms2.process_engine.core DefaultProcess]))

(def role-processes
  [{:name "get-security-roles"
    :runnable-fn (fn [{{user :current-user} :session}]
                   (some-kind-of-admin? user))
    :run-fn (fn [params]
              (let [user (get-in params [:session :current-user])
                    user-org-id (get-in user [:organization :id])
                    org-id (get-in params [:query-params :organization])
                    loc-id (get-in params [:query-params :location])]
                (cond
                 (super-admin? user)
                 (cond
                  org-id (json-str (data/find-children "organization" org-id "role"))
                  loc-id (json-str (data/find-children "location" loc-id "role"))
                  :else (json-str (data/find-all "role")))
                 (admin? user)
                 (cond
                  (and org-id (data/belongs-to? "user" (:id user) "organization" org-id))
                  (json-str (data/find-children "organization" org-id "role"))
                  (and loc-id (data/siblings? {:start-type "user" :start-id (:id user) :parent-type "organization" :parent-id user-org-id :sibling-type "location" :sibling-id loc-id}))
                  (json-str (data/find-children "location" loc-id "role"))
                  :else (json-str (data/find-children "organization" org-id "role"))))))
    :run-if-false forbidden-fn}

   {:name "get-security-role"
    :runnable-fn (fn [{{user :current-user} :session}]
                   (some-kind-of-admin? user))
    :run-fn (fn [params]
              (let [user (get-in params [:session :current-user])
                    user-org-id (get-in user [:organization :id])
                    role-id (get-in params [:query-params :role])]
                ;; (cond
                ;;  (super-admin? user)
                ;;  (json-str (data/find-record "role" role-id))
                ;;  (and (admin? user) (data/belongs-to? name)))
                ))}

   {:role "put-security-role"
    :runnable-fn (fn [params] true)
    :run-fn (fn [params]
              (let [role (:body-params params)]
                (json-str (data/create "role" role))))}

   {:name "post-security-role"
    :runnable-fn (fn [params] true)
    :run-fn (fn [params]
              (let [role-id (Integer/parseInt (get-in params [:query-params :role]))
                    role (-> params :body-params)]
                (json-str (data/update "role" role-id role))))}])

(process/register-processes (map #(DefaultProcess/create %) role-processes))