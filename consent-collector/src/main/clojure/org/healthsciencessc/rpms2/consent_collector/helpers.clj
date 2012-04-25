(ns org.healthsciencessc.rpms2.consent-collector.helpers
  "General purpose helpers used when creating views."
  (:require [hiccup
             [page :as hpage]
             [element :as helem]])
  (:require [ring.util.response :as ring])
  (:use [sandbar.stateful-session :only [session-get session-put! session-delete-key! destroy-session! flash-get]])
  (:use [clojure.tools.logging :only (debug info error)])
  (:use [clojure.string :only (replace-first join)])
  (:use [org.healthsciencessc.rpms2.consent-collector.i18n :only (i18n i18n-existing)]))

;; web application context, bound in core
(def ^:dynamic *context* "")

(defn absolute-path
  [& path-elements]
  (str *context* "/" (join "/" path-elements)))

(defn logged-in?
  []
  (if (session-get :user) true false))

(defn- remove-initial-slash
  [s]
  (if (re-matches #"\/.*" s)
    (subs s 1)
    s))

(defn mypath 
  "Converts the url to an absolute path, taking into account the context."
  [url]
  (absolute-path (remove-initial-slash url)))

(defn myredirect
  "Redirect, adding context information as needed."
  [url]
  (ring/redirect (mypath url)))

(defn username
  []
  (let [u (session-get :user)]
        (if u (:username u) nil)))

(defn text-field3
   "Returns a text field in a div. The keywords for the label
   and name of the text field are used to lookup the 
   strings in the resource bundle.
   
   Lookup the type and use that if it's available.  If no type
   is specified, type defaults to text.

   Example data in resource file:
   my-form-variable=loc1
   my-form-variable-label=Location 1
   my-form-variable-placeholder=Enter Location 1

   my-form-zipcode=zipcode
   my-form-zipcode-label=Zipcode
   my-form-zipcode-placeholder=Enter Zipcode
   my-form-zipcode-type=number
   "

;; Sample invocation (text-field3 "foo" "bar" :type "number" :required true)

   [form-name field-name & {:as input-opts}]

   (let [ placeholder-keyword (keyword (str form-name "-" field-name "-placeholder" ))
	type-keyword (keyword (str form-name "-" field-name "-type" ))
	type-value (i18n-existing type-keyword)
	t  (if type-value type-value "text") ]
   [:div.inputdata  {:data-role "fieldcontain" } 
      [:label {:for field-name :class "labelclass" } (i18n form-name field-name "label") ]
      [:input (merge { :type t :name field-name :placeholder (i18n placeholder-keyword) :length 100 } input-opts) ]]))


(defn submit-button
  "Returns standard submit button for form."
  [form-name]

  (let [kw (keyword (str form-name "-submit-button" ))]
    [:input {:type "submit" :value (i18n kw) :name (str form-name "-submit-button") } ]))

(defn standard-form 
  "Wraps form in a standard structure."
  [method action & body]

  [:div.standardForm 
   [:form {:method method :action action :data-ajax "false" } 
    body ] ])

(def progress-indicator
   "Using the current context, determines which
   progress dots should be displayed and displays them."
  [:span.progressArea "" ])


(defn- user-info
   [user]

   [:div#userinfo
     [:span.label (i18n :hdr-user-label) ] [:span.value (username) ]
     [:span.label  (i18n :hdr-location-label) ] [:span.value (session-get :location) ]
     [:span.label (i18n :hdr-organization-label) ] [:span.value (session-get :org-name) ]
     ;; if locked, show a lock symbol
     (if (session-get :lockcode) 
        [:span (i18n :hdr-locked) ]
        [:span (i18n :hdr-unlocked)])] )

(defn- header
 "The header displays page title, and information about logged in user."
 [title]
  [:div.header 
  [:div.ui-grid-b
   (if-let [msg (flash-get :header)]
      [:div#flash msg])
   (if-let [user (session-get :user)]
      [:div.userinfo (user-info user)])

   [:div.ui-block-a.title title ]
   [:div.ui-block-b "RPMS2" ]
   [:div.ui-block-c [:div 
       (if-let [nm (username)]
         (helem/link-to (mypath "/view/logout") (str "Logout " nm ))
         (helem/link-to (mypath "/view/login")  "Login" ))]]]])


(defn- patient-description
  []
  (let [[n id d] (map session-get [:patient-name :patient-id :patient-encounter-date])]
    (if n
      (format "Patient Name: <span class='value'>%s</span> Encounter ID: %s Date: %s" n id d)
      "RPMS2")))

(defn- patient-footer [] [:div.footer (patient-description) ])

(defn- non-patient-footer
  []
  [:div.footer 
  [:div.ui-grid-b
   [:div.ui-block-a 
	(if-let [nm (username)] 
		[:div (helem/link-to "/logout" (str "Logout " nm)) 
                      (helem/link-to "/login" "login" ) ]) ] 
   [:div.ui-block-b "RPMS2 (no patient)" ]
   [:div.ui-block-c (if-let [nm (username)]
                      [:div#header-userid nm
                       (if-let [loc (session-get :location)] (str " @ " loc)) ] )]]])

(defn- footer
  []
  (if (= (session-get :patient-id) "no patient") (non-patient-footer) (patient-footer) ))

(defn remove-session-data
  "Remove session data"
  []
  ;(destroy-session!)
  (doseq [k [ :patient-id 
            :patient-name 
            :patient-encounter-date 
            :location 
            :org-location 
            :org-name
            :lockcode
            :user  ]]
            (session-delete-key! k)))

(def ipad-html5-class
  "ui-mobile landscape min-width-320px min-width-480px min-width-768px min-width-1024px" )

(defn rpms2-page
  "Emits a standard RPMS2 page."
  [content & {:keys [title]}]

  (hpage/html5 {:class ipad-html5-class }
    [:head
    "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0\" >"
    (hpage/include-css 
     (absolute-path "app.css")
     "http://code.jquery.com/mobile/1.0.1/jquery.mobile-1.0.1.min.css" )

     ;; Add organization specify styles
     (if-let [org (session-get :org-name)]
      (hpage/include-css  (absolute-path (str org ".css")) ))

    (helem/javascript-tag "var CLOSURE_NO_DEPS = true;")
    (helem/javascript-tag (format "var RPMS2_CONTEXT = %s;" (pr-str *context*)))
    ;;(helem/javascript-tag (format "var GENERATED = %s;" (pr-str *context*)))
    (hpage/include-js 
     "http://code.jquery.com/jquery-1.6.4.min.js"
     "http://code.jquery.com/mobile/1.0.1/jquery.mobile-1.0.1.min.js"
     (absolute-path "app.js"))]
   [:body 
    [:div {:data-role "page" } 
    [:div#header {:data-role "header" } (header title) ]
    [:div#content {:data-role "content" } content]
    [:div#footer {:data-role "footer" } (footer)]]
    ]))

(defn rpms2-page-two-column
  "Emits a standard two-column RPMS2 page."
  [col1-content col2-content & {:keys [title]}]

  (rpms2-page 
	[:div.ui-grid-f
	     [:div.ui-block-a col1-content ]
	     [:div.ui-block-b col2-content ]]
        :title title ))
