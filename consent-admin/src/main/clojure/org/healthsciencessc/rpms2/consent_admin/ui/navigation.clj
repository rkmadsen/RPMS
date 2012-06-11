(ns org.healthsciencessc.rpms2.consent-admin.ui.navigation
  (:require [sandbar.stateful-session :as sess]
            [org.healthsciencessc.rpms2.consent-admin.security :as security]
            [org.healthsciencessc.rpms2.consent-domain.tenancy :as tenancy]
            [org.healthsciencessc.rpms2.consent-domain.roles :as roles]))

(defn- use?
  "Checks to see if a structure can be rendered into a navigation group or item."
  [target]
  (if-let [pred (:use? target)]
    (pred)
    true))
  
(defn- admin-or-super?
  "Checks if the current user is an administrator or super administrator."
  []
  (if-let [user (sess/session-get :user)]
    (or (roles/superadmin? user)(roles/admin? user))
    false))
  
(defn- admin?
  "Checks if the current user is an administrator."
  []
  (roles/admin? (sess/session-get :user)))

(defn- super?
  "Checks if the current user is a super administrator."
  []
  (roles/superadmin? (sess/session-get :user)))

(defn- designer?
  "Checks if the current user is an administrator or super administrator."
  []
  (roles/protocol-designer? (sess/session-get :user)))

(defn- manager?
  "Checks if the current user is an administrator or super administrator."
  []
  (roles/consent-manager? (sess/session-get :user)))

(defn- default-item-generator
  "Checks if the current user is an administrator or super administrator."
  [item]
  [:li.navitem [:a {:href "#" :onclick (str "PaneManager.stack('" (:url item) "', {}, {})") } (:label item)]])

(defn- location-item-generator
  "Item generator that checks if there is a replacement label for 'Location'"
  [item]
  (let [user (security/current-user)
        label (str (tenancy/label-for-location nil (:organization user)) (:label item))]
    [:li.navitem [:a {:href "#" :onclick (str "PaneManager.stack('" (:url item) "', {}, {})") } label]]))

(defn- consenter-item-generator
  "Item generator that checks if there is a replacement label for 'Consenter'"
  [item]
  (let [user (security/current-user)
        label (str (tenancy/label-for-consenter nil (:organization user)) (:label item))]
    [:li.navitem [:a {:href "#" :onclick (str "PaneManager.stack('" (:url item) "', {}, {})") } label]]))

(defn- protocol-location-item-generator
  "Checks if the current user is an administrator or super administrator."
  [item]
  (for [mapping (roles/protocol-designer-mappings (sess/session-get :user))]
    (let [loc (:location mapping)
          label (:name loc)
          id (:id loc)]
      [:li.navitem 
        [:a {:href "#" 
             :onclick (str "PaneManager.stack('" (:url item) "', {location: '" id "'}, {})") } label]]
    )))

(defn- default-group-generator
  "Generates the output of a group record."
  [group]
  (list [:h4.navlabel [:a {:href "#"} (:group group)]] 
        [:div.navpanel
          [:ul.navlist (for [item (:items group)]
                         (if (use? item)
                           (if (:generator item)
                             ((:generator item) item)
                             (default-item-generator item))))]]))


(defn- protocols-group-generator
  "Generates the output of a the Protocols group record."
  [group]
  (let [user (security/current-user)
        label (str (tenancy/label-for-protocol nil (:organization user)) (:group group))]
    (list [:h4.navlabel [:a {:href "#"} label]] 
          [:div.navpanel
           [:ul.navlist (for [item (:items group)]
                          (if (use? item)
                            (if (:generator item)
                              ((:generator item) item)
                              (default-item-generator item))))]])))

(def groupings 
  [
    {:group "Organization" :use? admin-or-super?
      :items [{:url "/view/organizations" :label "Manage All Organizations" :use? super?}
              {:url "/view/organization" :label "Organization Settings" :use? admin?}
              {:url "/view/locations" :label "s" :use? admin? :generator location-item-generator}]}
    {:group "Security" :use? admin?
      :items [{:url "/view/users" :label "Users"}
              {:url "/view/groups" :label "Groups"}
              {:url "/view/roles" :label "Roles"}]}
    ;; Following is for the Protocol group.
    {:group "s" :use? designer? :generator protocols-group-generator
      :items [{:url "/view/protocol/location" :label "Locations" :generator protocol-location-item-generator}]}
    {:group "Library" :use? designer?
      :items [{:url "/view/endorsement/types" :label "Endorsement Types"}
              {:url "/view/endorsements" :label "Endorsements"}
              {:url "/view/policy/definitions" :label "Policy Definitions"}
              {:url "/view/policies" :label "Policies"}
              {:url "/view/metaitems" :label "Meta Items"}
              {:url "/view/widgets" :label "Widgets"}]}
    {:group "Management" :use? manager?
      :items [{:url "/view/consenter/history" :label " History" :generator consenter-item-generator}
              {:url "/view/audit" :label "Audit"}]}
  ])

(defn- generate-group
  "Checks to see if a structure can be rendered into a navigation group or item."
  [group]
  (if-let [generator (:generator group)]
    (generator group)
    (default-group-generator group)))

(defn navigator
  "Generates the navigator menu."
  []
  (list [:div#navigator
          (for [group groupings]
            (if (use? group)
              (generate-group group)))]
        [:script 
"$(function() {
  $( \"#navigator\" ).accordion({
    collapsible: true,
    fillSpace: true,
    autoHeight: false
  });
});"]))
