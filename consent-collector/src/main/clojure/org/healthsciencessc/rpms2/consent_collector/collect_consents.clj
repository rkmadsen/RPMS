(ns ^{:doc "Collect consents - collects information from forms." }
  org.healthsciencessc.rpms2.consent-collector.collect-consents
  (:require
   [org.healthsciencessc.rpms2.consent-collector.dsa-client :as dsa]
   [org.healthsciencessc.rpms2.consent-collector.formutil :as formutil]
   [org.healthsciencessc.rpms2.consent-collector.helpers :as helper])
  (:use [sandbar.stateful-session :only [session-get session-put! flash-get flash-put! ]])
  (:use [clojure.tools.logging :only (debug info warn error)])
  (:use [clojure.pprint :only (pprint)])
  (:use [org.healthsciencessc.rpms2.consent-collector.debug :only [debug! pprint-str]])
  (:use [org.healthsciencessc.rpms2.consent-collector.persist :only [assemble-consents]])
  (:use [org.healthsciencessc.rpms2.consent-collector.config :only [config]])
  (:use [org.healthsciencessc.rpms2.consent-collector.i18n :only [i18n]]))


(def REVIEW_EDIT_BTN_PREFIX "redit_btn_")
(def REVIEW_META_EDIT_BTN_PREFIX "rmedit_btn_" )

(defn- widget-identifier
  [widget]
  (if (config "mock-data") (:name widget) (:id widget)))

(defn- widget-label
  [widget]
  (:label widget))  

(defn- dbg
  "Displays m only if verbose debugging is enabled."
  [m]

  (if (config "verbose-collect-consents") [:div.debug m ]))

(defn unimplemented-widget
  "Displays an unrecognized or unimplemented widget."

  [{:keys [widget value form review] :as m}]
  [:div [:span.standout "Unrecognized control " 
         "TYPE  " [:span.standout "TYPE " (:type widget) ] 
         "WIDGET " [:span.standout "WIDGET " widget ] 
         [:pre (pprint-str m) ]] 
   [:span.control-type  (:type widget) ] widget ])

 
(defn- gen-properties-to-map 
  [m]
  (apply merge {} (for [item m] (hash-map (keyword (:key item)) (:value item)))))
 

(defn- control
  "Displays widget by invoking the method with the widget's type.
  A map is passed in which contains the widget, the current value of the widget,
  the form and flag indicated whether this is in the review phase (so the widget
  can render itself appropriately)."

  [{:keys [widget value] :as m}]

  (let [ns "org.healthsciencessc.rpms2.consent-collector.collect-consents/"
        func  (if-let [f (resolve (symbol (str ns (:type widget))))] f unimplemented-widget)
        wid   (if (config "mock-data") 
                  widget
                 (merge widget (formutil/widget-props-localized widget))) 
        wdata (helper/data-for wid) 
        ]
      [:div (func (merge m {:value wdata :widget wid } )) 
            (dbg [:div [:span.standout (:name widget) " " 
                        (:id widget) " " (:type widget) ] 
                  [:span.data " VALUE [" wdata "]" ] widget ]) ]))
       
(defn- unlist
  "Handles case where a list is provided when a value is expected. 
  Treat a string as a value."
  [v]
  (if (coll? v)
      (apply str v)
      v))

(defn- get-sig-png
  "This finds the original signature capture widget through the endorsement that it provided"
  [end-id]
  (let [form (:form (session-get :published-version)) ;; bind currently loaded form
        original-widgets (into {} (for [w (formutil/list-widgets-in-form form)] ;; a map of endorsement->widget-id
                                    [(:endorsement (formutil/widget-properties w))
                                     (:id w)]))]
    (get (session-get :model-data)
         (keyword (get original-widgets end-id)))))

(defn- get-endorsement-label [end-id]
  (-> (session-get :published-version)
    :endorsements
    (get end-id)
    :label))

(defn review-endorsement
  "A ReviewEndorsement widget is used to review endorsements 
  collected during consent process."
  [{:keys [widget] :as m}]
  (let [props (formutil/widget-props-localized widget)
        png (get-sig-png (:endorsement props))]
    [:div.control.review-endorsement
     [:div.ui-grid-b
      [:div.ui-block-a.metadata
       (or (:title props)
           (get-endorsement-label (:endorsement props))
           "Review Endorsement")
       (helper/signaturePadDiv :name (widget-identifier widget) :value png :read-only? "true")]
      [:div.ui-block-c.metadata
       (helper/submit-btn {:value (or (:label props) "Edit Endorsement")
                           :name (str REVIEW_EDIT_BTN_PREFIX  (:returnpage props))})]]]))


(defn review-metaitem
  "Display meta item. The widget's meta-item id is the key into
  the current forms meta-data map."

  [{:keys [widget] :as m}]
  [:div.control.review 
     (let [meta-item-kw (keyword (:meta-item widget))
           entry (get (session-get :all-meta-data) meta-item-kw)
           mi-label (:name entry) ; (:label mitem)
           mi-value (:value entry)
           changed (= (:changed entry) "CHANGED") ]
    [:div.ui-grid-b
      [:div.ui-block-a.metadata mi-label]
      [:div.ui-block-b.metadata 
       [:span {:class (if changed "changed" "") } mi-value]]
      [:div.ui-block-c.metadata 
         (helper/submit-btn {:value (unlist (:label widget)) 
                             :name (str REVIEW_META_EDIT_BTN_PREFIX (:meta-item widget)) }) ]] )])

(defn find-policy
  [w]

  (let [p (unlist (:policy w))
        policies (helper/current-policies)]
        (get policies p)))

(defn- save-policy-kw
  [pos]
  (let [policy (unlist (:policy pos))]
     (keyword (str "policy-" policy ))))

(defn- lookup-policy-widget
  [widget]
  (let [md (session-get :model-data)]
   (get md (save-policy-kw widget)))) 

(defn review-policy 
  "A ReviewPolicy widget provides a controller that allows the collector to 
  review consents collected for policies during the review process.
  The value will be the value associated with the named widget.
  choicebuttons, policybutton checkbox-label

  Find the widget associated with the policy and use that value."
  [{:keys [widget] :as m}]
  [:div.control.review 
     (list 
       (dbg [:div
        [:div "FINISHED FORMS " (session-get :finished-forms) ]
        [:div "widget " (:title widget) 
          " returnpage " (helper/get-named-page (:returnpage widget)) ] ])

       (let [policy (find-policy widget)
             other (lookup-policy-widget widget) ]
           [:div.ui-grid-b
                [:div.ui-block-a.metadata (:title policy) ]  
                [:div.ui-block-b.metadata 
                (if (empty? other) [:div 
                                    "form validation issue RETURN PAGE "
                                    (:returnpage widget) 
                                    ])
                (let [v (helper/data-for other)]
                   (if other
                    (cond 
                      (= (:type other) "policy-choice-buttons")
                      [:div 
                       (if (= v "true-label") (unlist (:true-label other)))
                       (if (= v "false-label") (unlist (:false-label other))) ]

                      (= (:type other) "policy-checkbox")
                      (if (= "on" v)
                        (other :label)) 

                      (= (:type other) "policy-button")
                      (:label other) 

                      (= (:type other) "policy-text")
                      (do
                        (:label other) 
                        [:div "POLICY TEXT: " other ]
                       )

                      :else
                      [:p "OTHER TYPE " other])
                     )
                    ) 
                 ]
                [:div.ui-block-c.metadata 
                 (helper/submit-btn {:value (unlist (:label widget)) 
                                     :name (str REVIEW_EDIT_BTN_PREFIX (:returnpage widget)) })]])) ])

(defn media
  [{{:keys [name title] :as widget} :widget}]
  (let [{:keys [sources posters]} (formutil/widget-properties widget)
               videos-data (map vector posters sources (drop 1 (range)))]
    (list [:div.media-gallery
           [:h4.media-title title]
           [:div.video-thumbnails
            (for [[poster source id] videos-data]
                 (let [video-page (str name "_" id)]
                   (list [:a {:href (str "#" video-page) :data-rel "dialog" :data-role "button" :data-inline "true"}
                             [:img {:src poster :width 100 :height 100}]]
                         [:div {:id video-page :class "video-pages" :data-role "page" :data-fullscreen "true"}
                               [:div {:data-role "header"}
                                     [:h2 title]]
                               [:div {:data-role "content"}
                                     [:video
                                      {:controls ""
                                      :width "480"
                                      :height "300"}
                                      [:source {:src source}]]]
                               [:div {:data-role "footer"} [:h1 ""]]])))]]
          [:div.clear])))

(defn signature
  "Emits data for signature widget. A map with widgets state is passed
   to use in rendering the widget."
  [{:keys [widget value] :as m}]

  (if (config "skip-signatures")
  [:div "SKIPPING SIGNATURE" ]
  [:div.control.signature 
   (:name widget)
    [:div.ui-grid-b
       [:div.ui-block-a "" ]
       [:div.ui-block-b "" ]
       [:div.ui-block-c.right 
          [:a {:href "#"
               :data-role "button" 
               :data-theme "a" 
               :onclick "$('.sigPad').signaturePad().clearCanvas()"} 
         (:clear-label widget)]]]
   (helper/signaturePadDiv :name (widget-identifier widget) :value value)]))

(defn- true-or-not-specified? 
  [v]
  (not (= v false))) 

(defn- save-policy-widget
  [widget]

  (let [policy (unlist (:policy widget))
        wsav (dissoc widget :properties :contained-in :organization :contains)
        policy-kw (save-policy-kw widget)
        md (session-get :model-data)]

        (if (= nil policy)
           (println "WARNING POLICY IS NIL! - NOT SAVING " (pprint-str policy))
           (session-put! :model-data (assoc md policy-kw wsav)))))

(defn policy-text
  "A PolicyText widget generates title and paragraph from a specific Policy."  
  [{:keys [widget] :as m}]
  [:div.control.policy-text
   (list 
     (let [policy (find-policy widget)
           title (unlist (:title policy))
           txt (:text policy)]

     ;; Display title if :render-title is missing or true 
     ;; and policy has a title
       
     (if (= nil policy)
          (println "WARNING policy-text widget --> policy is " 
                (:policy widget) " definition is "  (pprint-str policy)))

     (list 
       (if (= nil policy) [:div "WARNING: Policy text - policy not defined: " 
                           (:policy widget) ])
       (if (and (true-or-not-specified? (:render-title widget))
                (:title policy))
           [:div [:h1.title (apply str (:title policy))]])

     ;; Display text if :render-text is missing or true and policy has text
       (if (and (not (= (:render-text widget) false))
                txt)
           (for [t txt] [:div.text t]))

     (if (not (= (:render-media widget) false)) 
       [:div.render-media "Render media controls here" ]) ))) ])

(defn policy-choice-buttons
  "Creates two buttons that allow you to opt in or opt out of one or more
  policies. The widget's state is passed in to set current selection."
  [{:keys [widget value] :as m}]

  (save-policy-widget widget)
  (let [group-name (widget-identifier widget)
        true-map (if (= value "true-label") {:checked "" } {})
        false-map (if (= value "false-label") {:checked "" } {}) ]
    [:div.control.policy-choice-buttons 
      [:fieldset {:data-role "controlgroup" }
   ;; true label
         [:input (merge {:type "radio" 
                          :name group-name
                          :id "true-label"
                          :value "true-label"
                         } true-map)]
         [:label {:for "true-label" } (unlist (:true-label widget)) ] 

   ;; false label
         [:input (merge {:type "radio" 
                          :name group-name
                          :id "false-label"
                          :value "false-label"
                         } false-map)]
         [:label {:for "false-label" } (unlist (:false-label widget))]]]))

(defn data-change
  "Displays meta-data item and a flag if it has been selected for change.
  Look up the flag in all-meta-data."
  [{:keys [widget value] :as m}]
  [:div.control.data-change
   (list 
     (dbg [:div "DATA CHANGE value " (pprint-str value) 
           " widget  " (pprint-str widget) ])
     (for [nm (:meta-items widget)] 
           (list
             (let [kw (keyword nm)
                   entry (get (session-get :all-meta-data) (keyword nm))
                   changed (= (:changed entry) "CHANGED")
                   mi-label (:name entry)
                   mi-value (:value entry) ]

                  [:div.ui-grid-b
                    [:div.ui-block-a.metadata mi-label ] 
                    [:div.ui-block-b.metadata 
                       [:span {:class (if changed "changed" "") 
                               :id nm } mi-value ]]  

                     [:input {:type "hidden" 
                              :id (str "hidden-" nm) 
                              :name (str helper/META_DATA_BTN_PREFIX nm)
                              ;:value "NO"
                              }]
                    [:div.ui-block-c 
                     [:p [:a 
                          {:href "#popup" 
                           :data-rel "dialog" 
                           :onclick "org.healthsciencessc.rpms2.core.data_change_clicked(this)"
                           :mdid (pr-str (str nm))
                           :data-role "button" 
                           :data-theme "a" } "Change" ] ]
                     ] ])))) ])

(defn policy-button
  "Displays the policy button. Once the button has been pushed,
  the data in the model is set and the style is changed." 

  [{:keys [widget value] :as m}]
  (save-policy-widget widget)
  [:div.control.policy-button 
   (helper/submit-btn {:data-theme (if value "b" "d" )
                       :data-inline "false"
                       :name (str helper/ACTION_BTN_PREFIX (widget-identifier widget))
                       :value (unlist (widget-label widget)) }) ])


(defn text
  "A Text widget generates a title and paragraph representations for 
  text values set on the control. The control requires that either the 
  title, the text, or both be set."
  [{:keys [widget] :as m}]
  [:div.control.text
   (if (:title widget) [:h1.title (apply str (:title widget)) ])
   (list (for [t (:text widget)] [:p (apply str t) ])) ])

(defn policy-checkbox
  "Displays checkbox, using the remembered state.  
  A hidden input is created so we can clear the checkbox from the data
  model if it is not checked and the form is submitted.
  Checkboxes are included in form submission parameters if they are not checked."
  [{:keys [widget value] :as m}]

  (save-policy-widget widget)
  (let [nm (widget-identifier widget)
        checked  (if (= value "on") {:checked "checked" } {})]
   [:div.control 
    [:input {:type "hidden" :name (str helper/CHECKBOX_BTN_PREFIX nm)} ]
    [:div {:data-role "fieldcontain"}
      [:input (merge {:type "checkbox" :id nm :name nm} checked ) ]
      [:label {:for nm} (widget-label widget)]]
    ]))

(defn- section
  "Creates section div containing all widgets in this section."
  [s]
  [:div.section (map #(control {:widget % 
                                :form (helper/current-form) 
                                }) (sort-by :order (:contains s))) ])


(defn- page-dbg
  [p]

  (dbg [:div.debug
       [:div.left "Page "  [:span.standout (:name (session-get :page)) ] " " (:title p)     
       " Form #" [:span.standout (inc (session-get :current-form-number))] " of " 
       [:span.standout (count (session-get :selected-protocol-version-ids )) ] 
       [:span "page keys " (pprint-str (keys p)) ]
             [:div "HEY " (pprint-str (gen-properties-to-map (:properties p))) ]
       ]
       [:div "Data  " (session-get :model-data) ] ]))

(defn- display-page
  "Displays sections. Checks for missing page
  and optionally displays debugging information.

  If page is available, displays each section of the page
  in a separate div." 

  [dm]

  ;(debug "PAGE " (pprint-str p))
  ;(println "DISPLAY PAGE " (pprint-str (session-get :page)))
  (let [p (session-get :page)]
    (list
      (if (= nil p) 
        [:h1 "Unable to show page - missing page " 
        (if-let [pn (session-get :page-name) ]
             [:span.standout pn ]) ])
    [:div (page-dbg p)
      (if (helper/in-review?) [:h1 "Summary" ] )
      [:div (map section (sort-by :order (:contains p))) ]])))

(defn- form-title
  [f]
  (unlist (:title f))) 

(defn- view-update-information
  [ctx nm]

  (let [meta-item-kw (keyword nm)
        entry (get (session-get :all-meta-data) meta-item-kw)
        mi-label (:name entry) 
        mi-value (:value entry) ]
      (helper/rpms2-page 
       [:div.collect-consent-form
          [:h2 "Update the following information: " ]
          (dbg [:div.debug (session-get :model-data) ])
          [:form {:action (helper/mypath "/collect/consents") 
                  :method "POST" 
                  :data-ajax "false" 
                  :data-theme "a" } 
            [:div.ui-grid-b
               [:div.ui-block-a mi-label]
               [:div.ui-block-b   
                     [:input {:name (str helper/META_DATA_UPDATE_BTN_PREFIX nm)
                              :value mi-value
                              }]]]
          [:div.submit-area 
           (helper/submit-btn {:value "Update" }) ]]] 
       :title "Update Information" )))


(defn- view-finished
  "All forms have been processed.  If in review, go to witness consents. 
  Otherwise, if finishing initial consent collection, display a Thank You page."
  [ctx]

  (if (helper/in-review?)
    (helper/myredirect "/witness/consents")
    (helper/rpms2-page 
      [:div.collect-consent-form
        [:form {:action (helper/mypath "/view/unlock") 
                :method "GET" 
                :data-ajax "false" 
                :data-theme "a" } 
         [:div.centered 
           (dbg [:div
                 (let [ff (session-get :finished-forms)]
                  [:div [:ol (for [f (keys ff) ]
                    [:li "Form " [:span.standout f ] (pprint-str (helper/pr-form (f ff) ) )] )]])])
           [:div.finished1 "Thank You!" ]
           [:div.finished2 "Your selected " (helper/org-protocol-label) "s are complete." ]
           [:div.finished3 "Please return the device." ] ]
         [:div.submit-area (helper/submit-btn {:value "Continue" :name "next" }) ]]] 
       :title "Consents Complete" ) ))

(defn- navigation-buttons
  "Displays the navigation button for the page, which will be
  a Continue button and optionally a previous button.
  Don't display the previous button if there's a pending return page."
  []

  [:div 
   (if (and (:previous (session-get :page))
            (not (helper/get-return-page)))
       (helper/submit-btn {:value "Previous" :name "previous" }))
   (helper/submit-btn {:value "Continue" :name "next" }) ]) 

(defn- emit-page
  []
  (helper/rpms2-page 
     (helper/collect-consent-form "/collect/consents"
         (display-page (session-get :model-data))
         (navigation-buttons)) 
       :title (form-title (helper/current-form)) 
       :second-page "placeholder" ))

(defn view 
  "Collect and review consents processes. Displays current page."
  ([] (view (session-get :model-data)))
  ([ctx]

  (debug "view() SESSION PAGE: " (:name (session-get :page)))

  ;; first time here, initialize 
  (if-let [s (session-get :collect-consent-status)]
    (debug "Already initialized: Page " (pprint-str (:name (session-get :page))))
    (helper/init-consents))

   (emit-page)))

(defn- get-matching-btns 
  "Get parameters with name starting with string 's'.
  Returns a list, which will be empty if there are no matches."
  [parms s]
  (filter #(.startsWith (str (name %)) s) (keys parms)))

(defn- find-review-edit-page
  [parms]
  (helper/find-special-page parms REVIEW_EDIT_BTN_PREFIX))

(defn- find-review-meta-edit-page
  [parms]
  (helper/find-special-page parms REVIEW_META_EDIT_BTN_PREFIX))

(defn- has-any?
  "Are there any parameters starting with the string 's'?"
  [parms s]
  (> (count (get-matching-btns parms s)) 0))

(defn get-next-page
  "Returns next page."
  []
  (if-let [nxt (:next (session-get :page))]
      (helper/get-named-page nxt)))


(defn perform
  "Collect consents."

  [{parms :body-params :as ctx}]

  (debug "288 perform " ctx)
  (let [nxt (get-next-page)]
    (helper/save-captured-data parms) 
    (cond 
      ; If there's a return page, go there
      (helper/get-return-page)  
      (let [pg-name (helper/get-return-page)]
         (do 
           (debug "GOING TO RETURN PAGE " pg-name)
           (helper/clear-return-page)
           (helper/set-page (helper/get-named-page pg-name))
           (view)))

      ; If the user wants to edit a reviewed item
      (find-review-edit-page parms)
      (let [pg-name (find-review-edit-page parms)]
            (do 
              (debug "GOING TO REVIEW EDIT PAGE: [" pg-name "]")
              (helper/save-return-page)
              (helper/set-page (helper/get-named-page pg-name))
              (view)))

      ; If user wants to edit meta data item
      (find-review-meta-edit-page parms)
      (let [pg-name (find-review-meta-edit-page parms)]
            (do 
              (debug "GOING meta-EDIT PAGE: [" pg-name "]")
              (helper/save-return-page)
              (view-update-information ctx pg-name)))

      ;; special buttons which are completely processed by save-captured-data
      (has-any? parms helper/ACTION_BTN_PREFIX )
      (view)

      (has-any? parms "signature-btn-")
      (view)

      (contains? parms :previous)
      (do 
         (if-let [pg-name (:previous (session-get :page)) ]
            (helper/set-page (helper/get-named-page pg-name)))
         (helper/myredirect "/collect/consents"))

      ;; if next page available
      nxt 
      (do (helper/set-page nxt)
          (view))

      (helper/finish-form)
      (if-let [pg (session-get :page)]
        (do
          (helper/set-page pg)
          (view {}))
        (view-finished ctx))

     :else
     (view-finished ctx))))
