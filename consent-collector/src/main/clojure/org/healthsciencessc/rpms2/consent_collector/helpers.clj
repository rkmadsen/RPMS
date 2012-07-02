(ns ^{:doc "General purpose helpers used when creating views"}
      org.healthsciencessc.rpms2.consent-collector.helpers
  (:require [hiccup
               [page :as hpage]
               [element :as helem]])
  (:require [ring.util.response :as ring])
  (:require [org.healthsciencessc.rpms2.consent-collector.dsa-client :as dsa])
  (:require [org.healthsciencessc.rpms2.consent-collector.formutil :as formutil])
  (:use [sandbar.stateful-session :only [session-get session-put! session-delete-key! destroy-session! flash-get flash-put!]])
  (:use [clojure.tools.logging :only (debug info error)])
  (:use [clojure.string :only (replace-first join)])
  (:use [slingshot.slingshot :only (try+)])
  (:use [org.healthsciencessc.rpms2.consent-domain.tenancy :only [label-for-location label-for-protocol]])
  (:use [org.healthsciencessc.rpms2.consent-collector.debug :only [debug! pprint-str]])
  (:use [org.healthsciencessc.rpms2.consent-collector.config :only [config]])
  (:use [org.healthsciencessc.rpms2.consent-collector.i18n :only (i18n i18n-existing
                                                                       i18n-label-for
                                                                       i18n-placeholder-for)]))

(def COLLECT_START_PAGE :collect-start)
(def REVIEW_START_PAGE :review-start)
(def OLD_REVIEW_START_PAGE :summary-start)
(def HAS_RETURN_PAGE :review-consent-page-in-progress)

(def ACTION_BTN_PREFIX "abtn_")
(def CHECKBOX_BTN_PREFIX "cb_btn_")
(def META_DATA_BTN_PREFIX "md_btn_")
(def META_DATA_UPDATE_BTN_PREFIX "md_btn_")

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
  "Redirect to url, adding context information as needed.
  If a message is specified and debugging is enabled, 
  the message is displayed as a flash message"
  ([i18n-key url]
   (if-let [b (config "verbose-collect-consents")]
     (do
      (debug "myredirect  " (i18n i18n-key) " url " url)
      (flash-put! :header (i18n i18n-key))
      (myredirect url))
     (myredirect url)))

  ([url]
  (debug "myredirect url " url)
  (ring/redirect (mypath url))))

(defn flash-and-redirect
  "Sets flash message and goes to the specified page."
  [i18n-key path]

  (debug "flash-and-redirect  " (i18n i18n-key) " path " path)
  (flash-put! :header (i18n i18n-key))
  (myredirect path))

(defn username
  "Extracts the username from the currently logged in user."
  []
  (let [u (session-get :user)]
        (if u (:username u) nil)))

(defn authorized-locations
  []
  (->> (session-get :user)
        :role-mappings
        (filter (comp #{"Consent Collector"} :name :role))
             (map :location)))

(defn org-location-label
  "Returns location label, which is taken from the user's location
  if available; otherwise this is taken from the organization."
  []
  (label-for-location (session-get :org-location) (:organization (session-get :user))))

(defn org-protocol-label
  "Returns protocol label, which is taken from the user's location
  if available; otherwise this is taken from the organization."
  []
  (label-for-protocol (session-get :org-location) (:organization (session-get :user))))

(defn submit-btn 
  "Standard submit button.  :name and :value should be define plus optionally other."
  ([] (submit-btn {}))

  ([input-opts]
   [:input (merge {:type "submit" 
                   :data-theme "a"
                   :data-role "button"
                   :data-ajax "false" 
                   :data-inline "true"
                  } input-opts) ] ))


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

  [:div.centered.post-form
     [:form {:action (mypath path) 
             :method "POST" 
             :data-ajax "false" 
             :data-theme "a" } 
      [:div.innerform.centered body ] 
      [:div.submit-area submit-buttons ] ]] ) 


(defn signaturePadDiv
  "Outputs a Signature Pad div which corresponds to custom sigpad
  styles (eg. related to size - width, height, the signature line,etc).
  see http://thomasjbradley.ca/lab/signature-pad/ "
  [& {:keys [name value read-only?]}]
  [:div.sigPad {:data-image value :data-read-only read-only?}
   ;[:div.sigNav
   ; [:div.clearButton (i18n :sig-clear-btn-label)]
   ; [:p.drawItDesc (i18n :signatureInstruction)]]
   ;[:div.sigError (i18n :signatureErrorInstruction)]
   [:div {:class "sig sigWrapper"}
    [:canvas.pad {:width 550 :height 200}]
    [:input.output {:type "hidden" :name name}]]])


(defn collect-consent-form 
  "the style is the only difference."
  [path body & submit-buttons ]

  (let [retval [:div.collect-consent-form
     [:form {:action (mypath path) 
             :method "POST" 
             :data-ajax "false" 
             :data-theme "a" } 
      [:div body ] 
      [:div.submit-area submit-buttons ] ]]] 
      ;;(debug "collect-consent-form " (pprint retval)) 
     retval)) 


(defn remove-session-data
  "Remove session data"
  []
  ;(destroy-session!)
  (debug "remove-session-data")
  (doseq [k [:consenter 
             :encounter
             :location 
             :org-location 
             :org-name
             :lockcode
             :search-params
             :create-params
             :witness-png
             :user]]
    (session-delete-key! k)))

(def ipad-html5-class
  "ui-mobile landscape min-width-320px min-width-480px min-width-768px min-width-1024px" )

(defn- logout-form
  []
  [:form {:method "POST" :action (absolute-path "/view/logout") }
           [:input {:type "submit" 
                    :name "logout-btn" 
                    :data-role "button"
                    :data-inline "true"
                    :data-theme "a"
                    :data-mini "true"
                    :data-ajax "false" 
                    :value "Logout" } ]])

(defn cancel-form
  [path]
  [:form {:method "GET" :action (absolute-path path)  }
           [:input {:type "submit" 
                    :name "cancel-btn" 
                    :data-role "button"
                    :data-inline "true"
                    :data-theme "a"
                    :data-mini "true"
                    :data-ajax "false" 
                    :value "Cancel" } ]])

(defn- header-collect-consents
  [title]
  [:div.header {:data-role "header" } 
     [:div#consent-header
     [:div.ui-grid-i
         [:div.ui-block-aa (cancel-form "/view/select/consenter") ]
         [:div.ui-block-bb.title title ]
         [:div.ui-block-cc (if-let [u (session-get :user)] (logout-form))]]] 
     [:div (if-let [msg (flash-get :header)] [:div#flash msg ]) ] ]) 

(defn- header-standard
  [title & cancel-btn]
  [:div.header {:data-role "header" } 
     [:div.ui-grid-b 
        [:div.ui-block-a cancel-btn ]
        [:div.ui-block-b.title title ]
        [:div.ui-block-c (if-let [u (session-get :user)] (logout-form))]] 
     [:div (if-let [msg (flash-get :header)] [:div#flash msg ]) ] ] )

(defn clear-return-page
  []
  (session-delete-key! HAS_RETURN_PAGE))

(defn get-return-page
  []
  (session-get HAS_RETURN_PAGE))

(defn save-return-page
  []
  (println "SAVING PAGE " (session-get :page-name))
  (session-put! HAS_RETURN_PAGE (session-get :page-name)))

(defn- header
  [title cancel-btn]
  (if (session-get :collect-consent-status) 
      (header-collect-consents title)
      (header-standard title cancel-btn)))

(defn in-review?
  "Returns true if in the review process."
  []
  (= (session-get :which-flow) REVIEW_START_PAGE))

(defn- after-content
  "Enable signature pad."
  []
  (hpage/include-js 
    (absolute-path "collect-sigpad.js")))

(defn format-consenter []
  (if-let [{:keys [first-name last-name consenter-id]}
           (session-get :consenter)]
    (format "Consenter: %s %s (%s)" first-name last-name consenter-id)))

(defn- footer
  []
  [:div.footer {:data-role "footer" :data-theme "c" } 
    [:div.ui-grid-b
      [:div.ui-block-a (if-let [loc (session-get :location)]
                               (str (org-location-label) ": " loc)) ]

      [:div.ui-block-b (format-consenter)]

      [:div.ui-block-c (if-let [p (:encounter-id (session-get :encounter))]
                               (str "Encounter ID: " p)) ] ]])


(defn rpms2-page
  "Emits a standard RPMS2 page."
  [content & {:keys [title cancel-btn end-of-page-stuff second-page]}]

  (let [resp (hpage/html5 {:class ipad-html5-class }
    [:head
    "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0\" >"
    "<meta name=\"apple-mobile-web-app-capable\" contents=\"yes\" />"
    (hpage/include-css 
     (absolute-path "app.css")
     (absolute-path "jquery.mobile-1.1.0.min.css" )
     (absolute-path "jquery.signaturepad.css" ))

    (helem/javascript-tag "var CLOSURE_NO_DEPS = true;")
    (helem/javascript-tag (format "var RPMS2_CONTEXT = %s;" (pr-str *context*)))
    (hpage/include-js 
     (absolute-path "jquery-1.7.1.min.js")
     (absolute-path "jquery.mobile-1.1.0.min.js")
      ;; see http://thomasjbradley.ca/lab/signature-pad/
     (absolute-path "jquery.signaturepad.js")
     (absolute-path "json2.min.js")
     (absolute-path "flashcanvas.js")
     (absolute-path "app.js")
     (absolute-path "rpms2.media.js"))]
   [:body 
    [:div {:data-role "page" :data-theme "a"  :id "one" }  
      (header title cancel-btn)
      [:div#content {:data-role "content" :data-theme "d" } 
      content
      (after-content)
      end-of-page-stuff ]
      (footer) 
     ]

    (if second-page
      [:div {:data-role "page" :id "popup" } 
       [:div {:data-role "header" :data-theme "d" } [:h1 "Data Change" ]]
       [:div {:data-role "content" :data-theme "d" } 
        [:div "This item has been flagged for change and may be edited during the review process." ]
        [:a {:href "#one"  
             :data-rel "back" 
             :data-role "button" 
             :data-inline "true"
             :data-icon "back"}]]])
    ])] 
      resp))


(defn rpms2-page-two-column
  "Emits a standard two-column RPMS2 page."
  [col1-content col2-content title cancel-btn]

  (rpms2-page 
	[:div.ui-grid-f
	     [:div.ui-block-a col1-content ]
	     [:div.ui-block-b col2-content ]]
        :title title 
        :cancel-btn cancel-btn ))

(defn radio-btn
  "Returns a radio button."

  ([group-name btn-name] 
     (radio-btn group-name btn-name btn-name))
  ([group-name btn-name btn-id]
     (radio-btn group-name btn-name btn-id {}))
  ([group-name btn-name btn-id m]

  (list [:input (merge {:type "radio" 
                 :name group-name
                 :id btn-id 
                 :value btn-name } m )]
        [:label {:for btn-name } btn-name ] )))

(defn radio-btn-group
  "Handles jquerymobile radio button group.
  {:btnlist collection :group-name g :selected-btn b}"
  [m]

  [:fieldset {:data-role "controlgroup" }
   (list (map 
           (fn [btn-name] 
             (radio-btn 
                (:group-name m) 
                btn-name 
                btn-name
                (if (= (:selected-btn m) btn-name) {:checked "checked" } {} ))) 
             (:btnlist m) )) ])

(defn checkbox-group
  "Returns checkbox group, with jquerymobile attributes."
  [m]
  [:div {:data-role "fieldcontain" }
   [:input (merge {:type "checkbox" 
                   :id (:name m) 
                   :name (:name m) } 
                   (if (= (:value m) "on") {:checked "checked" } {} )) ]
   [:label {:for (:name m) } (:label m) ]])

(defn gender-control
  "Creates drop down for selecting gender. "
  [field]

  [:div.inputdata {:data-role "fieldcontain" } 
     [:label {:for field :class "labelclass" } "Gender" ]
     [:select {:name field :id field }
        [:option {:value "F" } "Female" ]
        [:option {:value "M" } "Male" ]
     ]])

(defn emit-field
  "Emits a field definition. 
  Two variations - with or without map containing default values."

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
          (if (= specified-kind "gender")
             (gender-control field)

       [:div.inputdata {:data-role "fieldcontain" } 
            [:label {:for field
                     :class "labelclass" } 
                     (i18n-label-for form-name normalized-field) ]
            [:input m ]])))))

(defn current-form
  "Returns the current form which is being processed."
  []
  (-> (session-get :published-version) :form))

(defn- add-properties-to-map 
  [m]
  (apply merge {} (for [item m] (hash-map (keyword (:key item)) (:value item)))))

(defn get-named-page
  "Find page named 'n' in form 'f'
  Add data in properties to the resulting page."
  [n]

  (debug "Get named page |" n "|")
  (let [f (current-form)
        raw-page (first (filter #(= (:name %) n ) (:contains f) ))
        page (merge raw-page (add-properties-to-map (:properties raw-page))) 
        ]
        page))


(defn- wid-id
  "Return widget identifier (name for mock data, id for published-versions data)."
  [widget]
  (if (config "mock-data") (:name widget) (:id widget)))

(defn data-for
  ([c] 
  (get (session-get :model-data) (keyword (wid-id c))))
  ([c dm] (get dm (keyword (wid-id c)))))



(defn- keyword-from-button 
  "Remove prefix from the string and turn it into a keyword."
  [btns ^String prefix]
  (keyword (subs (name (first btns)) (count prefix))))

(defn strip-prefix
  [^String prefix s]
  (subs (name s) (count prefix)))

(defn- get-matching-btns 
  "Get parameters with name starting with string 's'.
  Returns a list, which will be empty if there are no matches."
  [parms s]
  (filter #(.startsWith (str (name %)) s) (keys parms)))

(defn find-real-names 
  "Finds all names from a parameters by removing a prefix.
  e.g. review-edit-btn-page1 will return page1 if str1 is review-edit-btn-"
  [parms str1]
  (let [len (count str1)]
        (map (fn [n] (subs (name n) len)) (get-matching-btns parms str1))))

(defn find-special-page
  "Finds a page name from a parameters by removing a prefix.
  e.g. review-edit-btn-page1 will return page1 if str1 is review-edit-btn-"
  [parms str1 ]
  (first (find-real-names parms str1)))

(defn- update-meta-item
  "Add the key value to the specified meta item.
  Used for updating the value and the changed marker.

  mi is a keyword of the meta-data item id"
  [mi m]

  (let [orig-map (session-get :all-meta-data)  ; original meta-data map
        entry (orig-map mi)  ; the entry for this meta-data item
        new-entry (merge entry m)  ; merge in the new values 
        new-map (assoc orig-map mi new-entry) ]

        (debug "Updating meta data item: mi is " mi " new " m )
        #_(println "Updating meta data item: mi is " (select-keys new-entry [:id :value :name])  " added " m )
        (session-put! :all-meta-data new-map)))

(defn- handle-meta-data
  "Remove special meta data keys.  If there is a meta data with value CHANGED, 
  remove the meta data prefix and set it in the :all-meta-data.
  There should only be one at a time."
  [m] 

  (let [meta-btns (filter #(and (.startsWith (str (name %)) META_DATA_BTN_PREFIX) 
                                (= (get m %) "CHANGED")) (keys m))
        ;; remove any meta data buttons that are not changed
        keep-keys (filter #(or (and (.startsWith (str (name %)) META_DATA_BTN_PREFIX) 
                                       (= (get m %) "CHANGED")) 
                               (not (.startsWith (str (name %)) META_DATA_BTN_PREFIX)))
                               (keys m)) ]
       (if (> (count meta-btns) 0) ; save data for changed meta item using the name without the prefix
         (do
           (println "META BUTTONS " (pprint-str meta-btns))
           (println "all parms BUTTONS " (pprint-str (dissoc m :consenter)))
           (update-meta-item (keyword-from-button meta-btns META_DATA_BTN_PREFIX) 
                                  {:value (get m (first meta-btns))
                                   :changed "CHANGED" } )
           (assoc (select-keys m keep-keys)
              (keyword-from-button meta-btns META_DATA_BTN_PREFIX ) "CHANGED")
           )
           (select-keys m keep-keys))))

(defn- handle-action-btns
  "Handles action buttons, which is once set must always stay set. 
  Only one action button will be present at a time.
  If there is an action button, adds it to the map (where it will
  get merged into the model as part of further processing)"
  [m]

  (let [btns (filter #(.startsWith (str (name %)) ACTION_BTN_PREFIX) (keys m))
        retval (if (> (count btns) 0)
           (assoc m (keyword-from-button btns ACTION_BTN_PREFIX) "selected")
            m) ] 
        (debug "handle action button: " retval)
        ;(println "handle action button: " retval)
     retval))

(defn- handle-meta-data-update-btns
  "Handles update meta-data buttons."
  [parms]

  (let [btns (filter #(.startsWith (str (name %)) META_DATA_UPDATE_BTN_PREFIX) (keys parms))
        mdata (session-get :model-data)]
     (if (> (count btns) 0)
       (let [mi (keyword-from-button btns META_DATA_UPDATE_BTN_PREFIX) 
             val (mi parms) 
             marker (keyword (str META_DATA_BTN_PREFIX (name mi))) 
             marker2 (keyword (name mi)) ]

             (update-meta-item mi {:value val :changed "NO" })

             ; maybe this needs to be changed finished-forms
             (session-put! :model-data (dissoc mdata marker marker2))
             (dissoc parms marker marker2))
          parms)))

(defn remove-checkboxes-from-model
  "Find checkboxes that are on the page but weren't submitted
  as form parameters - any checkboxes that aren't checked, but were
  previously set in the model.

  A hidden field that starts with CHECKBOX_BTN_PREFIX will be on each
  page with a checkbox, which is used to find these missing checkboxes.

  parms is map of post parameters.  

  e.g. if the map contains :cb-btn-TissueCheckbox but not :TissueCheckbox,
  then unset :TissueCheckbox in the model.

  This is needed because the checkbox does not get sent on a form submission
  if the checkbox is not checked.
  "
  [parms]
  (let [col (find-real-names parms CHECKBOX_BTN_PREFIX)
        cbs (set (map keyword col))]
        (if (> (count cbs) 0) 
            (doall (for [nm (into [] cbs)]
                 (session-put! :model-data 
                               (dissoc (session-get :model-data) 
                                       (keyword nm))))))))

(defn save-captured-data
  "Updates model with information from the form parameters. 
  Start with original model, remove checkboxes on this page (which
  won't be sent with post parameters if not checked), merge in the 
  parameters after doing any special handling
  for action buttons, and metadata items."

  [parms]

  (debug "ENTER save captured data: " (pprint-str (session-get :model-data) ))
  (remove-checkboxes-from-model parms) ;; may modify :model-data
  (if (in-review?)
      (handle-meta-data-update-btns parms)) ;; may modify :model-data
  (let [ m (session-get :model-data)
        new-map (->  m
                     (merge (-> parms
                                (dissoc :next :previous) 
                                handle-action-btns
                                handle-meta-data-update-btns
                                handle-meta-data))
                     (dissoc (get-matching-btns parms CHECKBOX_BTN_PREFIX)) )

        keep-keys (filter #(not 
                             (or
                              (.startsWith (name %) CHECKBOX_BTN_PREFIX)
                              ;(.startsWith (name %) ACTION_BTN_PREFIX)
                              (.startsWith (name %) META_DATA_UPDATE_BTN_PREFIX)
                              )) (keys new-map))
        fmap (select-keys new-map keep-keys) ]
        (session-put! :model-data fmap)
        (debug "EXIT save captured data: " (pprint-str fmap))
        ;(println "EXIT save captured data: " (pprint-str fmap))
        fmap))
                 

(defn clear-consents
  "Remove any in-progress consent information."
  []
  (debug "clearing consent data")
  (doseq [k [:collect-consent-status
             :model-data
             :witness-png]]
    (session-delete-key! k)))

(defn print-form
  "If orig-n is a sequence, use the first item.
  Otherwise, use orig-n, then return the protocol name."
  [orig-n]
  (let [n (if (seq? orig-n) (first orig-n) orig-n)]
     (get-in n [:protocol :name])))

(defn- get-form-name-kw
  "Each form is named by form-n where n is the number."
  [n]
  (keyword (str "form-" n)) )

(defn pr-form
  "Prints the form without the :output value.
  Want to replace keys starting with out"
  [ff]
  (if (map? ff)
      (let [okeys (filter #(.startsWith (name %) "output") (keys ff))]
        (if (> (count okeys) 0) 
            (str "okeys: " (list okeys) " " (pprint-str (dissoc ff (flatten okeys))))
            (pprint-str ff)))
      (str "not map " (pprint-str ff))))

(defn current-policies 
  "Returns policies for form being processed."
  []
  (-> (session-get :published-version) :policies))


(defn- get-finished-form-key
  [n]
  (let [pvlist (session-get :selected-protocol-version-ids)]
    (if (< n (count pvlist))
      (keyword (nth pvlist n))
      nil)))

(defn- get-saved-finished-form
  "Returns finished form n or nil if n is not a valid form."
  [n]
  (let [pvlist (session-get :selected-protocol-version-ids)
        finished-form-key (get-finished-form-key n)]
    (if finished-form-key
        (get (if-let [f (session-get :finished-forms)] f {}) 
             finished-form-key)
        nil)))

(defn- get-data-for-nth-finished-form
  "If there is data saved for the nth form, return it.
  Otherwise return an empty map." 
  [n]
  (debug "get data for nth form " n (count (session-get :finished-forms)))
  (let [pvlist (session-get :selected-protocol-version-ids) ]
        (if-let [sf (get-saved-finished-form n)] sf {})))

(defn finish-form
  "Save data of current form and prepare for next one."
  []
  (debug "FINISH FORM")
  (let [mdata (session-get :model-data)
        n     (session-get :current-form-number)
        fkey (get-finished-form-key n)
        orig-finished-forms (if-let [ff (session-get :finished-forms)] ff {}) ] 
    (do
      ;; save current forms data into :finished-forms
      (if fkey 
        (session-put! :finished-forms (assoc orig-finished-forms fkey mdata)))  

      ;; if we are in review then load up saved data for the form 
      (if (in-review?) 
          (session-put! :model-data (get-data-for-nth-finished-form (inc n)))
          (session-put! :model-data {} ))

      (debug "FINISHED FORM: " (inc n) " " (session-get :model-data))

      (session-put! :current-form-number (inc n))
      (if-let [next-published-version (dsa/get-nth-form (inc n))]
        (do
          (println "GOT ANOTHER FORM")
          (session-put! :published-version next-published-version))
          (let [formval (current-form)
                start-page-nm ((session-get :which-flow) (current-form))
                p (get-named-page start-page-nm)]
            (session-put! :page p) 
            (session-put! :page-name (:name p)) 
         )))))

(defn- init-flow 
  "Initializes consent collection data structures.
  Sets :form to current form, initializes :page "
  [which-flow]

  (debug "init-flow: " which-flow)
  (let [form (dsa/get-nth-form 0)]
    (do
      (session-put! :current-form-number 0)
      (session-put! :published-version form)
      (session-put! :which-flow which-flow)))

  (let [pg-nm (which-flow (current-form) )
        _ (debug "init-flow pg-name " pg-nm)
        raw-page (get-named-page pg-nm)
        page (if raw-page 
               (merge raw-page (add-properties-to-map (:properties raw-page))) 
               nil) ]

    (session-put! :page page)
    (session-put! :page-name pg-nm)

    (if-not (session-get :page) 
       (do (debug "init-flow MISSING PAGE " which-flow)
           (debug "init-flow MISSING PAGE form " (current-form) )
           (flash-put! :header (str "MISSING PAGE [" pg-nm "]" ))
           (error "init-flow MISSING PAGE: " pg-nm )))))

(defn init-review
  "Initializes consent collection data structures
  for review."
  []
  (init-flow REVIEW_START_PAGE)
  (session-put! :model-data (get-data-for-nth-finished-form 0)))

(defn set-page
  [raw-page]

  (debug "Setting page: " (:name raw-page) " " raw-page)
  (let [page (if raw-page 
               (merge raw-page (add-properties-to-map (:properties raw-page))) 
               nil)]
    (session-put! :page page) 
    (session-put! :page-name (:name page)) ))

(defn init-consents
  "Initializes the consent collection data structures
  for collection."
  []
  (session-put! :collect-consent-status "true") 
  (init-flow COLLECT_START_PAGE ))

(defn clear-location
  "Removes location information from session."
  []
  (session-delete-key! :location)
  (session-delete-key! :org-location))

(defn clear-consenter
  "Removes consenter information from session."
  []
  (doseq [k [:protocols
             :consenter
             :encounter
             :search-params
             :collect-consent-status
             :selected-language
             :witness-png
             :needed-protocol-ids
             :finished-forms]]
    (session-delete-key! k)))

(defn set-consenter
  "Saves consenter info in the session."
  [c]
  (session-put! :consenter c))

;(debug! handle-meta-data-update-btns)
;(debug! handle-meta-data)
