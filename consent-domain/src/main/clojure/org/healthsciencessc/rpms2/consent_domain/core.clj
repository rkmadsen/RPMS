(ns org.healthsciencessc.rpms2.consent-domain.core
  (:use [org.healthsciencessc.rpms2.consent-domain types]
        [slingshot.slingshot :only (throw+)]))

(def base
  {:id {:persisted false}
   :active {:persisted true :omit true}})

(def person
  {:first-name {:persisted true}
   :middle-name {:persisted true}
   :last-name {:persisted true}
   :title {:persisted true}
   :suffix {:persisted true}})

(def default-data-defs
  {organization {:attributes
                 (merge base
                        {:name {:required true :persisted true}
                         :code {:persisted true :unique true}
                         :location-label {:persisted true}
                         :protocol-label {:persisted true}
                         :consenter-label {:persisted true}})}

   user {:attributes (merge base
                            person
                            {:username {:persisted true :required true :unique true}
                             :password {:omit true :persisted true :required true :validation (fn [password] (if (> 5 (count password)) "Password must be longer than 5 characters."))}})
         :relations [{:type :belongs-to :related-to organization :relationship :owned-by :required true :deletable-by-parent true}
                     {:type :belongs-to :related-to group :relationship :in-group}
                     {:type :has-many :related-to role-mapping}
                     {:type :has-many-through :related-to role-mapping :relation-path [group]}]}

   role {:attributes (merge base
                            {:name {:persisted true :required true}
                             :code {:persisted true}
                             :requires-location {:persisted true}})
         :relations [{:type :belongs-to :related-to organization :relationship :owned-by :required true :deletable-by-parent true}]}

   language {:attributes (merge base
                                {:name {:persisted true :required true}
                                 :code {:persisted true}})
             :relations [{:type :belongs-to :related-to organization :relationship :owned-by :required true :deletable-by-parent true}]}

   location {:attributes (merge base
                                {:name {:persisted true}
                                 :code {:persisted true}
                                 :protocol-label {:persisted true}
                                 :consenter-label {:persisted true}})
             :relations [{:type :belongs-to :related-to organization :relationship :owned-by :required true :deletable-by-parent true}]}

   group {:attributes (merge base
                             {:name {:persisted true}
                              :code {:persisted true}})
          :relations [{:type :belongs-to :related-to organization :relationship :owned-by :required true :deletable-by-parent true}
                      {:type :has-many :related-to role-mapping}]}

   consenter {:attributes (merge base
                                 person
                                 {:consenter-id {:persisted true :required true}
                                  :gender {:persisted true :required true}
                                  :dob {:persisted true :required true}
                                  :zipcode {:persisted true :required true}})
              :relations [{:type :belongs-to :related-to organization :relationship :owned-by :required true :deletable-by-parent true}
                          {:type :has-many :related-to encounter}
                          {:type :belongs-to :related-to location :relationship :in-location}]}

   encounter {:attributes (merge base
                                 {:encounter-id {:persisted true :required true}
                                  :date {:persisted true :required true}})
              :relations [{:type :belongs-to :related-to organization :relationship :has-organization :deletable-by-parent true}
                          {:type :belongs-to :related-to consenter :relationship :has-consenter :omit-rels true}
                          {:type :belongs-to :related-to location :relationship :has-location}]}

   role-mapping {:attributes base
                 :relations [{:type :belongs-to :related-to user :relationship :has-user :omit-rels true :deletable-by-parent true}
                             {:type :belongs-to :related-to group :relationship :has-group :omit-rels true :deletable-by-parent true}
                             {:type :belongs-to :related-to role :relationship :has-role :required true :deletable-by-parent true}
                             {:type :belongs-to :related-to organization :relationship :has-organization :required true :deletable-by-parent true}
                             {:type :belongs-to :related-to location :relationship :has-location :deletable-by-parent true}]}

   meta-item {:attributes (merge base
                                 {:name {:persisted true}
                                  :description {:persisted true}
                                  :uri {:persisted true}
                                  :data-type {:persisted true}
                                  :default-value {:persisted true}
                                  :status {:persisted true}})
              :relations [{:type :belongs-to :related-to organization :relationship :owned-by :required true :deletable-by-parent true}
                          {:type :belongs-to :related-to text-i18n :relationship :has-label :name :label :can-create-parent true}]}

   policy {:attributes (merge base
                              {:name {:persisted true}
                               :description {:persisted true}
                               :uri {:persisted true}
                               :code {:persisted true}
                               :status {:persisted true}})
           :relations [{:type :belongs-to :related-to organization :relationship :owned-by :required true :deletable-by-parent true}
                       {:type :belongs-to :related-to text-i18n :relationship :has-title :name :title :can-create-parent true}
                       {:type :belongs-to :related-to text-i18n :relationship :has-text :name :text :can-create-parent true}]}

   policy-definition {:attributes (merge base
                                         {:name {:persisted true}
                                          :description {:persisted true}
                                          :code {:persisted true}
                                          :status {:persisted true}})
                      :relations [{:type :belongs-to :related-to organization :relationship :owned-by :required true :deletable-by-parent true}]}

   form {:attributes (merge base
                            {:name {:persisted true}
                             :code {:persisted true}
                             :status {:persisted true}})
         :relations [{:type :belongs-to :related-to organization :relationship :owned-by :required true :deletable-by-parent true}
                     {:type :has-many :related-to widget}]}

   widget {:attributes (merge base
                              {:name {:persisted true}
                               :type {:persisted true}
                               :status {:persisted true}})
           :relations [{:type :belongs-to :related-to organization :relationship :owned-by :required true :deletable-by-parent true}
                       {:type :belongs-to :related-to form :relationship :in-form}
                       {:type :belongs-to :related-to widget :relationship :contained-in :name :contained-in :omit-rels true}
                       {:type :has-many :related-to widget :name :contains}
                       {:type :has-many :related-to widget-property :name :properties}]}

   widget-property {:attributes (merge base
                                       {:key {:persisted true}
                                        :value {:persisted true}})
                    :relations [{:type :belongs-to :related-to widget :relationship :has-widget :omit true :required true :deletable-by-parent true}
                                {:type :belongs-to :related-to language :relationship :in-language}]}

   endorsement {:attributes (merge base
                                   {:name {:persisted true}
                                    :code {:persisted true}
                                    :status {:persisted true}})
                :relations [{:type :belongs-to :related-to organization :relationship :owned-by :deletable-by-parent true}
                            {:type :belongs-to :related-to endorsement-type :relationship :has-type :omit-rels true}
                            {:type :belongs-to :related-to text-i18n :relationship :has-label :name :label :can-create-parent true}]}

   endorsement-type {:attributes (merge base
                                        {:name {:persisted true}
                                         :code {:persisted true}
                                         :uri {:persisted true}
                                         :status {:persisted true}})
                     :relations [{:type :belongs-to :related-to organization :relationship :owned-by :required true :deletable-by-parent true}]}

   protocol {:attributes (merge base
                                {:name {:persisted true}
                                 :description {:persisted true}
                                 :protocol-id {:persisted true}
                                 :code {:persisted true}
                                 :required {:persisted true}
                                 :select-by-default {:persisted true}})
             :relations [{:type :belongs-to :related-to organization :relationship :owned-by :required true :deletable-by-parent true}
                         {:type :belongs-to :related-to location :relationship :located-at}]}

   protocol-version {:attributes (merge base
                                        {:status {:persisted true}
                                         :version {:persisted true}})
                     :relations [{:type :belongs-to :related-to organization :relationship :owned-by :required true :deletable-by-parent true}
                                 {:type :belongs-to :related-to protocol :relationship :version-of :required true :deletable-by-parent true}
                                 {:type :belongs-to :related-to form :relationship :described-by}
                                 {:type :many-to-many :related-to policy :name :policies :relationship :has-policy :omit-rels true}
                                 {:type :many-to-many :related-to endorsement :relationship :has-endorsement :omit-rels true}
                                 {:type :many-to-many :related-to language :relationship :has-language :omit-rels true}
                                 {:type :many-to-many :related-to meta-item :relationship :has-meta-item :omit-rels true}]}

   text-i18n {:attributes (merge base
                                 {:value {:persisted true}})
              :relations [{:type :belongs-to :related-to language :relationship :in-language :required true}]}})

(def default-value-types
  [role policy-definition language endorsement-type meta-item])

(def audit-relationships
  {:create  :created-by
   :update  :modified-by
   :destroy :deleted-by})

(defn get-relations
  "Returns all the relation maps for a given type"
  [type data-defs]
  (get-in data-defs [type :relations]))

(defn get-parent-relations
  "Returns the relation maps for a given type that describe relationships to parent types"
  [type data-defs]
  (filter :relationship (get-relations type data-defs)))

(defn get-parent-relation
  "Returns the relation map of the child that describes the relationship to the given parent type"
  [parent-type child-type data-defs]
  (first
   (filter #(= parent-type (:related-to %))
           (get-parent-relations child-type data-defs))))

(defn get-child-relations
  "Returns the relation maps for a given type that describe relationships to children types"
  [type data-defs]
  (filter #(not (:relationship %)) (get-relations type data-defs)))

(defn get-parent-relationship
  "Returns the relationship as a keyword between the parent type and child type"
  [parent-type child-type data-defs]
  (let [parent-relation (get-parent-relation parent-type child-type data-defs)]
    (:relationship parent-relation)))

(defn record-relations
  "Returns a collection of relation maps that should be used to construct the record."
  [type data-defs]
  (remove :omit (get-relations type data-defs)))

(defn get-attrs
  [type data-defs & attr-types]
  (let [attrs (get-in data-defs [type :attributes])]
    (if attr-types
      (for [[name props] attrs :when (and props (every? props attr-types))]
        name)
      (keys attrs))))

(defn required-rels
  [type data-defs]
  (filter :required (get-in data-defs [type :relations])))

(defmulti relation-name->key
  (fn [relation]
    ((:type relation)
     {:belongs-to :parent
      :has-many :children
      :has-many-through :children
      :many-to-many :children})))

(defmethod relation-name->key :parent
  [{:keys [related-to name]}]
  (or name (keyword related-to)))

(defmethod relation-name->key :children
  [{:keys [related-to name]}]
  (or name (keyword (str related-to "s"))))

(defn get-relation-name
  "Returns the name of the relation to be used in constructing records"
  [relation]
  (or (:name relation)
      (relation-name->key relation)))

(defn all-valid-keys
  [{:keys [attributes relations]}]
  (concat (map first (remove #(-> % second :omit) attributes))
          (map get-relation-name relations)))

(defn get-relationship-from-child
  [parent-type child-type data-defs]
  (let [relation (first (filter #(= parent-type (:related-to %))
                                (:relations (data-defs child-type))))]
    (:relationship relation)))

(defn get-directed-relationship
  [start-type end-type data-defs]
  (let [parent-relationship (get-parent-relationship start-type end-type data-defs)
        child-relationship (get-parent-relationship end-type start-type data-defs)]
    (cond
     parent-relationship {:dir :in :rel parent-relationship}
     child-relationship {:dir :out :rel child-relationship})))

(defn validate-record
  [record type data-defs]
  (->> (data-defs type)
       all-valid-keys
       (select-keys record)))

(defn validate-persistent-record
  [record type data-defs]
  (select-keys record (get-attrs type data-defs :persisted)))
