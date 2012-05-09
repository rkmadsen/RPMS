(ns org.healthsciencessc.rpms2.consent-collector.dsa-client
  (:require [clojure.string :as s]
            [clj-http.client :as http])
  (:import org.apache.http.auth.MalformedChallengeException
           org.apache.http.client.ClientProtocolException)
  (:use [slingshot.slingshot :only (try+)])
  (:use [org.healthsciencessc.rpms2.consent-collector  [config :only (config)]
                                                       [debug :only (debug!)] ]
        [clojure.tools.logging :only (debug info error warn)]
        [clojure.pprint :only (pprint)]
        [clojure.data.json :only (read-json json-str)]))

(def ^:dynamic *dsa-auth* nil)

(def consenter-search-fields  [:first-name
                               :last-name
                               :consenter-id
                               :dob
                               :zipcode])


(def create-consenter-fields [ :first-name
                               :middle-name
                               :last-name
                               :title
                               :suffix
                               :consenter-id
                               :gender
                               :dob
                               :zipcode ])


(def consenter-field-defs { 
                               :first-name          { :required true }
                               :middle-name         {}
                               :last-name           { :required true }
                               :title               {}
                               :suffix              {}
                               :consenter-id        { :required true }
                               :gender              { :required true :type "gender" :x18n-name "gender" }
                               :dob                 { :required true :type "date" :i18n-name "date-of-birth"}
                               :zipcode             { :required true :type "number" } 
})


;;consider using values from domain-services
(def create-consenter-required-fields [ :first-name
                                        :last-name 
                                        :gender
                                        :dob
                                        :zipcode ])

(defn- build-url 
   "Builds url for DSA for given path."
   [path]
   (let [ dsa-url (config "rpms2.dsa.url")
      no-slashes (fn [s] (second (re-matches #"/*([^/].*[^/])/*" s)))
      mypath  (if dsa-url 
                  (str (no-slashes dsa-url) "/" (no-slashes path)) 
                  (do 
                      (warn "WARNING: No dsa-url configured "  path)
                      #_(str "http://olocalhost:8080/" path )
                      (str "http://obis-rpms-neodb-dev.mdc.musc.edu:8080/services/" path)
                    ))] 
      (debug "Using dsa-url: " mypath )
      mypath))

;; TODO - find out why the auth isn't working right (we shouldn't
;; be getting this exception)
(defn- request
  "like http/request but doesn't crash on failed auth"
  [req]
  (try+ 
    (do
      (debug (str "request REQ: " req))
      (let [resp (http/request  req )]
        (debug (str "request RESPONSE: " resp))
        resp))
    (catch ClientProtocolException e
      ;; TODO -- check if cause is a MalformedChallengeException
      (do 
        (error (str "FAILED: " req) )
        (error (str "ClientProtocol Exception " (.getMessage e) )
                   ) {:status 401}))
    (catch java.net.UnknownHostException ex
        ;; we want to define flash message here
        (do 
            (debug "UNKNOWN HOST " ex)
           {:status 500 :error-message (str "Unknown host: " ex) }))
    (catch slingshot.ExceptionInfo ex
      (do (error "SLINGSHOT EXCEPTION" ex)
        {:status 403  :body (pr-str "INVALID REQUEST " ex " request: "  req)}))

    (catch Exception ex 
      (do 
        (debug "SOME OTHER ERROR: " ex)
        {:status 999  :body (pr-str "INVALID REQUEST " ex " request: "  req)}))
    (catch Object obj 
      (do 
        (error "http/request failed: object error " obj)
        (error "==http request failed --> " (pprint obj))
        {:status (:status obj) :body (print-str "OBJ INVALID REQUEST - see logs for details" )}))))

;; where to catch exceptions
;;  java.net.UnknownHostException
(defn dsa-call
  [process-name arguments]
  (let [[_ method path-dashes] 
        (re-matches #"(get|post|put|delete)-(.+)" (name process-name))
        method (keyword method),
        path (s/replace path-dashes "-" "/")
        maybe-parse-json
        (fn [{:keys [content-type status body headers] :as resp}]
          (debug "status is " status " body is " body )
          (if (= 403 status) 
              (do (println "FORBIDDEN") {:status 403} )
            (if (= 200 status)
              (try 
                (assoc resp :json (read-json body))
                (catch Exception ex (do (debug "WARNING: BODY IS NOT JSON " body ) resp) ))
              resp))) ]
    
    ;; try catch here?
    (-> (if (= :get method)
          (if (empty? arguments)
            {}
            {:query-params arguments})
          {:body (json-str arguments) })
        (assoc :request-method method
               :basic-auth *dsa-auth* 
               ; :content-type "text/clojure"
               :content-type "application/json"
               :url (build-url path) )
        request
        maybe-parse-json
      )))

(defn authenticate
  "Call security/authenticate userid password"
  [user-id password]
  (binding [*dsa-auth* [user-id password]]
    (dsa-call :get-security-authenticate {})))

(defn- remove-blank-vals
  "Given a map, removes all key/val pairs for which the value
  is blank."
  [m]
  (into {}
        (for [[k v] m :when (not (s/blank? v))]
          [k v])))

(defn- has-all-required-fields 
  "Given a map, ensures all required fields are there. 
  Returns a list of missing fields."
  [m required-fields]

  (if (= (count (select-keys m required-fields)) (count required-fields))
    nil
    (pr-str "All of these are required: " required-fields)))

(defn dsa-search-consenters
  "Search consenters."

  [params org-id]
  (debug "dsa-search-consenters PARAMS = " params " ORG " org-id)
  (let [consenter-params (remove-blank-vals
                          (select-keys params consenter-search-fields)) ]
      (dsa-call :get-consent-consenters (assoc consenter-params :organization org-id))))

(defn dsa-create-consenter
  "Create a consenter."
  [params]
  (debug "dsa-create-consenter PARAMS = " params)
  (let [p (remove-blank-vals (select-keys params create-consenter-fields)) 
        invalid (has-all-required-fields p create-consenter-required-fields)]
      (if invalid 
          (do (debug "INVALID - CANNOT CREATE " p  " invalid msg " invalid)
              {:status 409 :body "Validation failed - Please enter all required values" })
          (do (debug "dsa-create-consenter P = " p  " count " (count p))
              (dsa-call :put-consent-consenter p) ))))
      

(def protocol-names [ 
	"Lewis Blackman Hospital Patient Safety Act Acknowledgeement" 
	"Consent for Medical Treatment" 
	"Medicare" 
	"Tricare" ])

(def data-mappings {
   :location [ :id :name :code :protocol-label :organization ]
   :meta-item [ :id :name :description :data-type :default-value :organization ]
   :protocol [ :id :name :description :protocol-id :code :required :select-by-default :organization :location ]
})

(defn- id
  []
  (rand-int 1000000000))


(defn generate-meta-data-items
 []
 (list

{ :id (id) :name "additional-guarantor" :description "Additional guarantor" :data-type "string" :organization "MYORG" }
{ :id (id) :name "referring-doctor" :description "Date admitted" :data-type "string" :organization "MYORG" }
{ :id (id) :name "referring-doctor-city" :description "" :data-type "string" :organization "MYORG" }
{ :id (id) :name "primary-care-physician" :description "" :data-type "string " :organization "MYORG" }
{ :id (id) :name "primary-care-physician-city" :description "" :data-type "string" :organization "MYORG" }
{ :id (id) :name "attending-physician" :description "" :data-type "string" :organization "MYORG" }
{ :id (id) :name "advanced-directives-given" :description "" :data-type "yes-no" :organization "MYORG" }
{ :id (id) :name "admission-date" :description "Date admitted" :data-type "string" :organization "MYORG" }
{ :id (id) :name "form-signer" :description "Signer" :data-type "choice - patient or patient rep" :organization "MYORG" }

  )
)

(defn generate-protocol
  [prototype]
  { :name  (:name prototype)
    :description (if (:description prototype) (:description prototype) "description for protocol")
    :protocol-id "generated protocol-id"
    :code "description for protocol"
    :required (if (:required prototype) (:required prototype) false )
    :select-by-default (if 
	(:select-by-default prototype) 
	(:select-by-default prototype) false )
    :organization "description for protocol"
    :location "description for protocol"
  })

(defn get-protocols
  []
  (list 
    (generate-protocol {:name "Lewis Blackman Hospital Patient Safety Act Acknowledgeement" :select-by-default true :required true :description "Inform patient of right of access to attending physician" } ) 

    (generate-protocol {:name "Consent for Medical Treatment" :select-by-default false :required false :description "Some consent for medical treatment stuff " } ) 

    (generate-protocol {:name "Medicare" :select-by-default false :required false :description "Medicare stuff" } ) 

    (generate-protocol {:name "Tricare" :select-by-default true :required false :description "Tricare stuff" } ) ))
