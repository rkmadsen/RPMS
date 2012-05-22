(ns org.healthsciencessc.rpms2.consent-collector.helpers
  "General purpose helpers used when creating views."
  (:require [hiccup
               [page :as hpage]
               [element :as helem]])
  (:require [ring.util.response :as ring])
  (:use [sandbar.stateful-session :only [session-get session-put! session-delete-key! destroy-session! flash-get flash-put!]])
  (:use [clojure.tools.logging :only (debug info error)])
  (:use [clojure.string :only (replace-first join)])
  (:use [clojure.data.json :only (read-json json-str)])
  (:use [clojure.pprint])

  (:use [org.healthsciencessc.rpms2.consent-collector.debug :only [debug! pprint-str]])
  (:use [org.healthsciencessc.rpms2.consent-collector.i18n :only (i18n i18n-existing
                                                                       i18n-label-for
                                                                       i18n-placeholder-for)]))

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

(defn flash-and-redirect
  "Sets flash message and goes to the specified page."
  [i18n-key path]

  (debug "flash-and-redirect  " (i18n i18n-key) " path " path)
  (flash-put! :header (i18n i18n-key))
  (myredirect path))

(defn username
  []
  (let [u (session-get :user)]
        (if u (:username u) nil)))

(defn standard-submit-button 
  "Standard submit button.  :name and :value should be define plus optionally other."
  [input-opts]
  [:input (merge { :type "submit" 
                   :data-theme "a"
                   :data-role "button"
                   :data-inline "true"
                   } input-opts) ])

(defn submit-button
  "Returns submit button for form."
  ([form-name]
    (submit-button (i18n form-name "submit-button")
                   (str "-submit-button"))) 
  ([v n] 
    [:input 
      {:type "submit" 
       :data-theme "a"
       :data-role "button"
       :data-inline "true"
       :value v :name n } ])


  ([form-name v n] 
    [:input 
      {:type "submit" 
       :data-theme "a"
       :data-role "button"
       :data-inline "true"
       :value v :name n } ])
  )

(defn post-form 
  [path body & submit-buttons ]

  (let [retval  [:div.centered.post-form
     [:form {:action (mypath path) 
             :method "POST" 
             :data-ajax "false" 
             :data-theme "a" } 
      [:div.innerform.centered body ] 
      [:div.submit-area submit-buttons ] ]]] 
      (debug "post-form " (pprint retval)) retval)) 


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
            :search-params
            :create-params
            :user  ]]
            (session-delete-key! k)))

(def ipad-html5-class
  "ui-mobile landscape min-width-320px min-width-480px min-width-768px min-width-1024px" )

(defn get-input-type
  [form-name field-name]

  (let [ type-keyword (keyword (str form-name "-" field-name "-type" ))
      type-value (i18n type-keyword)
      t  (if type-value (i18n type-value) "text") ]
    (println "t is " t)))


(defn- logout-form
  []
  [:form {:method "POST" :action (absolute-path "/view/logout") }
           [:input {:type "submit" 
                    :name "logout-btn" 
                    :data-role "button"
                    :data-inline "true"
                    :data-theme "a"
                    :data-mini "true"
                    :value "Logout" } ]])

(defn- header-collect-consents
  [title]
  [:div.header {:data-role "header" } 
     [:div.ui-grid-b 
         [:div.ui-block-a 
          (list (let [s (:state (session-get :collect-consent-status))]
             "COLLECT CONSENTS " (:state s)
           #_(if-let [p (:name (:page s))] 
            " Page: " p))) ]
         [:div.ui-block-b.title title ]
         [:div.ui-block-c (if-let [u (session-get :user)] (logout-form))]] 
     [:div (if-let [msg (flash-get :header)] [:div#flash msg ]) ] ]) 

(defn- header-standard
  [title]
  [:div.header {:data-role "header" } 
     [:div.ui-grid-b 
        [:div.ui-block-a ]
        [:div.ui-block-b.title title ]
        [:div.ui-block-c (if-let [u (session-get :user)] (logout-form))]] 
     [:div (if-let [msg (flash-get :header)] [:div#flash msg ]) ] ] )

(defn- header
  [title]
  (if (session-get :collect-consent-status) 
      (header-collect-consents title)
      (header-standard title)))

(defn- footer
  []
  [:div.footer {:data-role "footer" :data-theme "c" } 
    [:div.ui-grid-b
      [:div.ui-block-a (if-let [loc (session-get :location)]
                               (str "Location: " loc)) ]

      [:div.ui-block-b (if-let [p (session-get :patient-name)]
                               (str "Consenter: " p)) ]

      [:div.ui-block-c (if-let [p (session-get :encounter-id)]
                               (str "EncounterID: " p)) ] ]])


(defn rpms2-page
  "Emits a standard RPMS2 page."
  [content & {:keys [title]}]

  (let [resp (hpage/html5 {:class ipad-html5-class }
    [:head
    "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0\" >"
    "<meta name=\"apple-mobile-web-app-capable\" contents=\"yes\" />"
    (hpage/include-css 
     (absolute-path "app.css")
     ;;"http://code.jquery.com/mobile/1.0.1/jquery.mobile-1.0.1.min.css" 
     "http://code.jquery.com/mobile/1.1.0/jquery.mobile-1.1.0.min.css" 
      )

    (helem/javascript-tag "var CLOSURE_NO_DEPS = true;")
    (helem/javascript-tag (format "var RPMS2_CONTEXT = %s;" (pr-str *context*)))
    (hpage/include-js 
     ;;"http://code.jquery.com/jquery-1.6.4.min.js"
     ;;"http://code.jquery.com/mobile/1.0.1/jquery.mobile-1.0.1.min.js"
     "http://code.jquery.com/jquery-1.6.4.min.js"
     "http://code.jquery.com/mobile/1.1.0/jquery.mobile-1.1.0.min.js"
     (absolute-path "app.js"))
     ]
   [:body 
    [:div {:data-role "page" :data-theme "a"  }  
     (header title)
      #_[:div.header {:data-role "header" } 
         [:div.ui-grid-b 
            [:div.ui-block-a ]
            [:div.ui-block-b.title title ]
            [:div.ui-block-c (if-let [u (session-get :user)] (logout-form))]] 
         [:div (if-let [msg (flash-get :header)] [:div#flash msg ]) ] ]
      [:div#content {:data-role "content" :data-theme "d" } content]
      (footer)
     ]])] 
      (debug "Page: " title " is\n" (pprint-str resp) "\n\n")
      resp))

(defn rpms2-page-two-column
  "Emits a standard two-column RPMS2 page."
  [col1-content col2-content title]

  (rpms2-page 
	[:div.ui-grid-f
	     [:div.ui-block-a col1-content ]
	     [:div.ui-block-b col2-content ]]
        :title title ))

(defn emit-field
  "Emits a field definition. 
  Two variations - with or without a map containing default values."

  ([field-def form-name field ]  
    (emit-field field-def form-name field {}))

  ([field-def form-name field default-values]

  (list (let [specified-kind (:type field-def)
              t (if (and specified-kind (not (= specified-kind "gender"))) 
                  specified-kind 
                  "text")
              normalized-field (if-let [n (:i18n-name field-def)] n field)

       mx {:type t 
           :class "inputclass" 
           :id field
           :name field
           :placeholder (i18n-placeholder-for form-name normalized-field)
          }

       _ (debug "AAA FIELD NAME ORIG " field " defaults " default-values " defaults for this field " (get default-values (keyword field)))
       ;; should also verify that is a valid value
       m  (merge mx 
            ;;(if (= "readonly" augment) {:readonly ""} {})
            (if (= t "date") {:data-theme "d" 
                              :data-role "datebox" 
                             } {})
            (if (:required field-def) {:required ""} {})
            (if-let [val-fn (:default-value field-def) ] 
              {:value (val-fn)} 
              (if-let [v (get default-values (keyword field))] {:value v } {})))
        ]

       [:div.inputdata {:data-role "fieldcontain" } 
            [:label {:for field
                     :class "labelclass" } 
                     (i18n-label-for form-name normalized-field) ]
            [:input m ]]))))

