(ns org.healthsciencessc.rpms2.consent-admin.ui.navigation
  (require [sandbar.stateful-session :as sess]
           [org.healthsciencessc.rpms2.consent-domain.roles :as roles]))

(defn- use?
  "Checks to see if a structure can be rendered into a navigation group or item."
  [ctx target]
  (if-let [pred (:use? target)]
    (pred ctx target)
    true))
  
(defn- admin-or-super?
  "Checks if the current user is an administrator or super administrator."
  [ctx target]
  true)

(defn- designer?
  "Checks if the current user is an administrator or super administrator."
  [ctx target]
  (roles/protocol-designer? (sess/session-get :user)))

(defn- manager?
  "Checks if the current user is an administrator or super administrator."
  [ctx target]
  (roles/consent-manager? (sess/session-get :user)))

(defn- default-item-generator
  "Checks if the current user is an administrator or super administrator."
  [ctx group item]
  [:li.navitem [:a {:href "#" :onclick (str "PaneManager.stack('" (:url item) "', {}, {})") } (:label item)]])

(defn- protocol-location-item-generator
  "Checks if the current user is an administrator or super administrator."
  [ctx group item]
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
  [ctx group]
  (list [:h4.navlabel [:a {:href "#"} (:group group)]] 
        [:div.navpanel
          [:ul.navlist (for [item (:items group)]
                         (if (use? ctx item)
                           (if (:generator item)
                             ((:generator item) ctx group item)
                             (default-item-generator ctx group item))))]]))

(def records 
  [
    {:group "Organization" ;; :use? admin-or-super?
      :items [{:url "/view/organization" :label "Settings"}
              {:url "/view/locations" :label "Locations"}]}
    {:group "Security" ;; :use? admin-or-super?
      :items [{:url "/view/users" :label "Users"}
              {:url "/view/groups" :label "Groups"}
              {:url "/view/roles" :label "Roles"}]}
    {:group "Protocols" ;; :use? designer?
      :items [{:url "/view/protocol/location" :label "Locations" :generator protocol-location-item-generator}]}
    {:group "Library" ;; :use? designer?
      :items [{:url "/view/policy/definitions" :label "Policy Definitions"}
              {:url "/view/policies" :label "Policies"}
              {:url "/view/metaitems" :label "Meta Items"}
              {:url "/view/widgets" :label "Widgets"}]}
    {:group "Management" ;; :use? manager?
      :items [{:url "/view/consenter/history" :label "Consenter History"}
              {:url "/view/audit" :label "Audit"}]}
  ])

(defn navigator
  ""
  [ctx]
  (list [:div#navigator
          (for [record records]
            (if (use? ctx record)
              (if (:generator record)
                ((:generator record) ctx record)
                (default-group-generator ctx record))))]
        [:script 
"$(function() {
  $( \"#navigator\" ).accordion({
    collapsible: true,
    fillSpace: true,
    autoHeight: false
  });
});"]
  ))
