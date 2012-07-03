(ns org.healthsciencessc.rpms2.consent-admin.process.users
  (:require [org.healthsciencessc.rpms2.process-engine.core :as process]
            [org.healthsciencessc.rpms2.process-engine.path :as path]
            [org.healthsciencessc.rpms2.consent-admin.config :as config]
            [org.healthsciencessc.rpms2.consent-admin.ajax :as ajax]
            [org.healthsciencessc.rpms2.consent-admin.security :as security]
            [org.healthsciencessc.rpms2.consent-admin.ui.layout :as layout]
            [org.healthsciencessc.rpms2.consent-admin.ui.container :as container]
            [org.healthsciencessc.rpms2.consent-admin.ui.actions :as actions]
            [org.healthsciencessc.rpms2.consent-admin.ui.selectlist :as selectlist]
            [org.healthsciencessc.rpms2.consent-admin.ui.form :as formui]
            [org.healthsciencessc.rpms2.consent-admin.services :as service]
            [sandbar.stateful-session :as sess]
            [hiccup.core :as html]
            [hiccup.element :as elem]
            [hiccup.form :as form]
            [ring.util.response :as rutil])
  (:use [clojure.pprint])
  (:import [java.io PrintWriter]
    [org.healthsciencessc.rpms2.process_engine.core DefaultProcess]))

(defn format-name
  [{:keys [first-name last-name middle-name]}]
  (str last-name ", " first-name " " middle-name))

(defn layout-users
  [ctx]
  (let [users (sort-by #(vec (map % [:last-name :first-name])) (service/get-users))]
    (if (service/service-error? users)
      (ajax/error (meta users))
      (layout/render ctx "Users"
        (container/scrollbox
          (selectlist/selectlist {:action :.detail-action}
            (for [x users]
              {:label (format-name x) :data (select-keys x [:id])})))
        (actions/actions 
             (actions/details-action {:url "/view/user/edit" :params {:user :selected#id}
                            :verify (actions/gen-verify-a-selected "User")})
             (actions/new-action {:url "/view/user/add"})
             (actions/back-action))))))

(def user-fields ;; probably should be i18nized
  (let [text-fields [:first-name "First Name"
                     :middle-name "Middle Name"
                     :last-name "Last Name"
                     :suffix "Suffix"
                     :title "Title"
                     :username "Username"]]
    (concat (map #(zipmap [:name :label] %)
                 (partition 2 text-fields))
            [{:type :password
              :name :password
              :label "Password"}])))

(defn render-user-fields
  "Create some field boxes from a map of [kw text-label]"
  ([] (render-user-fields {}))
  ([user]
    (map formui/record->editable-field 
         (repeat user)
         user-fields))) 

(defn get-view-user-add
  [ctx]
  (let [org (:organization (:query-params ctx))
        org-param (if org {:params {:organization org}})
        persist-params (merge {:method :post :url "/api/user/add"} org-param)]
  (layout/render ctx (if org "Create Admin" "Create User")
                 (container/scrollbox (formui/dataform (render-user-fields)))
                 (actions/actions
                   (actions/save-action persist-params)
                   (actions/back-action)))))

(defn get-view-user-edit
  [ctx]
  (if-let [user-id (-> ctx :query-params :user)]
    (let [user (service/get-user user-id)]
      (if (service/service-error? user)
        (ajax/error (meta user))
        (layout/render ctx "Edit User"
                   (container/scrollbox (formui/dataform (render-user-fields user)))
                   (actions/actions 
                     (actions/save-action {:method :post :url "/api/user/edit" :params {:user user-id}})
                     (actions/details-action {:url "/view/roles/show" :params {:assignee-type :user :assignee-id user-id} :label "Roles"})
                     (actions/delete-action {:url "/api/user" :params {:user user-id}})
                     (actions/back-action)))))))

(defn post-api-user-add
  [ctx]
  (let [org-id (or (:organization (:query-params ctx))
                   (:id (:organization (sess/session-get :user))))
        user-to-add (-> (:body-params ctx)
                      (select-keys (map :name user-fields))
                      (assoc :organization {:id org-id}))
        resp (if (:organization (:query-params ctx))
               (service/add-admin user-to-add)
               (service/add-user user-to-add))]
    (if (service/service-error? resp)
      (ajax/error (meta resp))
      (ajax/success resp))))

(defn post-api-user-edit
  [ctx]
  (let [keys (select-keys (:body-params ctx)
                          (map :name user-fields))
        resp (service/edit-user (-> ctx :query-params :user)
                                keys)]
    (if (service/service-error? resp)
      (ajax/error (meta resp))
      (ajax/success resp))))

(defn delete-api-user
  [ctx]
  (let [user (:user (:query-params ctx))
        resp (service/delete-user user)]
    (if (service/service-error? resp)
      (ajax/error (meta resp))
      (ajax/success resp))))

(def process-defns
  [{:name "get-view-users"
    :runnable-fn (constantly true)
    :run-fn  layout-users}
   {:name "get-view-user-add"
    :runnable-fn (constantly true)
    :run-fn get-view-user-add}
   {:name "delete-api-user"
    :runnable-fn (constantly true)
    :run-fn delete-api-user}
   {:name "get-view-user-edit"
    :runnable-fn (constantly true)
    :run-fn get-view-user-edit}
   {:name "post-api-user-edit"
    :runnable-fn (constantly true)
    :run-fn post-api-user-edit}
   {:name "post-api-user-add"
    :runnable-fn (constantly true)
    :run-fn post-api-user-add}
   ])

(process/register-processes (map #(DefaultProcess/create %) process-defns))
