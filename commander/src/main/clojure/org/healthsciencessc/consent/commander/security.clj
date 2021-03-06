(ns org.healthsciencessc.consent.commander.security
  (:require [pliant.webpoint.common :as common]
            [pliant.webpoint.url :as url]
            [ring.util.response :as response]
            [sandbar.stateful-session :as sandbar])
  (:use [compojure.core]))

(defn is-authenticated?
  []
  (let [{:keys [username password]} (sandbar/session-get :user)]
    (and username password)))

(defn ensure-auth-handler
  [handler]
  (fn [request]
    (let [path (common/path request)]
      (if (or (is-authenticated?)
              (= "/login" path)
              (= "/security/login" path))
        (handler request)
        (response/redirect (url/root-link request "/login"))))))

(defn current-user
  "Method used to get the current user for the request.  Multi interface allows for use in common runnables functions."
  ([& ignore] (sandbar/session-get :user)))

(defn current-org
  "Obtains the organization from the current user.  If out of session returns nil."
  [& ignore]
  (:organization (current-user)))

(defn current-org-id
  "Obtains the organization from the current user.  If out of session returns nil."
  [& ignore]
  (get-in (current-user) [:organization :id]))