(ns org.healthsciencessc.rpms2.consent-domain.core
  (:use [slingshot.slingshot :only (throw+)]))

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
  {"organization" {:attributes
                   (merge base
                          {:name {:required true :persisted true}
                           :code {:persisted true}
                           :location-label {:persisted true}
                           :protocol-label {:persisted true}})}

   "user" {:attributes (merge base
                              person
                              {:username {:persisted true :required true}
                               :password {:omit true :persisted true :required true :validation (fn [password] (< 5 (count password)))}})
           :relations [{:type :belongs-to :related-to "organization" :relationship :owned-by}
                       {:type :belongs-to :related-to "group" :relationship :in-group}
                       {:type :has-many :related-to "role-mapping"}
                       {:type :has-many-through :related-to "role-mapping" :relation-path ["group"]}]}

   "role" {:attributes (merge base
                              {:name {:persisted true :required true}
                               :code {:persisted true}})
           :relations [{:type :belongs-to :related-to "organization" :relationship :owned-by}]}

   "language" {:attributes (merge base
                                  {:name {:persisted true :required true}
                                   :code {:persisted true}})
               :relations [{:type :belongs-to :related-to "organization" :relationship :owned-by}]}

   "location" {:attributes (merge base
                                  {:name {:persisted true}
                                   :code {:persisted true}
                                   :protocol-label {:persisted true}})
               :relations [{:type :belongs-to :related-to "organization" :relationship :owned-by}]}

   "group" {:attributes (merge base
                               {:name {:persisted true}})
            :relations [{:type :belongs-to :related-to "organization" :relationship :owned-by}]}

   "consenter" {:attributes (merge base
                                   person
                                   {:consenter-id {:persisted true :required true}
                                    :gender {:persisted true :required true}
                                    :dob {:persisted true :required true}
                                    :zipcode {:persisted true :required true}})
                :relations [{:type :belongs-to :related-to "organization" :relationship :owned-by}
                            {:type :belongs-to :related-to "location" :relationship :in-location}]}

   "role-mapping" {:attributes base
                   :relations [{:type :belongs-to :related-to "user" :relationship :has-user :omit true}
                               {:type :belongs-to :related-to "group" :relationship :has-group :omit true}
                               {:type :belongs-to :related-to "role" :relationship :has-role}
                               {:type :belongs-to :related-to "organization" :relationship :has-organization}
                               {:type :belongs-to :related-to "location" :relationship :has-location}]}

   "meta-item" {:attributes (merge base
                                   {:name {:persisted true}}
                                   {:description {:persisted true}}
                                   {:uri {:persisted true}}
                                   {:data-type {:persisted true}}
                                   {:default-value {:persisted true}})
                :relations [{:type :belongs-to :related-to "organization" :relationship :owned-by}]}

   "policy" {:attributes (merge base
                                {:name {:persisted true}}
                                {:description {:persisted true}}
                                {:uri {:persisted true}}
                                {:code {:persisted true}})
             :relations [{:type :belongs-to :related-to "organization" :relationship :owned-by}]}

   "policy-defintion" {:attributes (merge base
                                          {:name {:persisted true}}
                                          {:description {:persisted true}}
                                          {:code {:persisted true}})
                       :relations [{:type :belongs-to :related-to "organization" :relationship :owned-by}]}

   "form" {:attributes (merge base
                              {:name {:persisted true}}
                              {:code {:persisted true}})
           :relations [{:type :belongs-to :related-to "organization" :relationship :owned-by}
                       {:type :has-many :related-to "widget"}]}

   "widget" {:attributes (merge base
                                {:name {:persisted true}}
                                {:type {:persisted true}})
             :relations [{:type :belongs-to :related-to "organization" :relationship :owned-by}
                         {:type :belongs-to :related-to "form" :relationship :in-form}
                         {:type :belongs-to :related-to "widget" :relationship :contained-in :name :contained-in}
                         {:type :has-many :related-to "widget" :name :contains}]}})

(defn get-relationship-from-relation
  [relation]
  (let [{:keys [name relationship]} relation]
    (or name relationship)))

(defn get-relations
  [type data-defs]
  (get-in data-defs [type :relations]))

(defn get-parent-relations
  [type data-defs]
  (filter #(= :belongs-to (:type %)) (get-relations type data-defs)))

(defn get-child-relations
  [type data-defs]
  (filter #(= :has-many (:type %)) (get-relations type data-defs)))

(defn get-parent-relationship
  [parent-type child-type data-defs]
  (let [parent-relation (first
                         (filter #(= parent-type (:related-to %))
                                 (get-parent-relations child-type data-defs)))]
    (get-relationship-from-relation parent-relation)))

(defn get-child-relationship
  [parent-type child-type data-defs]
  (let [child-relation (first
                        (filter #(= child-type (:related-to %))
                                (get-child-relations parent-type data-defs)))]
    (get-relationship-from-relation child-relation)))

(defn record-relations
  [type data-defs]
  (remove :omit (get-relations type data-defs)))

(defn attr-search
  [term]
  (fn [[attr desc]]
    (if (term desc)
      attr)))

(defn required-attrs
  [data-def]
  (keep (attr-search :required)
        (:attributes data-def)))

(defn persisted-attrs
  [data-def]
  (keep (attr-search :persisted)
        (:attributes data-def)))

(defmulti relation-name->key
  (fn [relation]
    ((:type relation)
     {:belongs-to :parent
      :has-many :children
      :has-many-through :children})))

(defmethod relation-name->key :parent
  [{:keys [related-to name]}]
  (or name (keyword related-to)))

(defmethod relation-name->key :children
  [{:keys [related-to name]}]
  (or name (keyword (str related-to "s"))))

(defn get-relation-name
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
    (get-relationship-from-relation relation)))

(defn get-directed-relationship
  [start-type end-type data-defs]
  (let [parent-relationship (get-parent-relationship start-type end-type data-defs)
        child-relationship (get-parent-relationship end-type start-type data-defs)]
    (cond
     parent-relationship {:dir :in :rel parent-relationship}
     child-relationship {:dir :out :rel child-relationship})))

(defn validate
  [record]
  record)

(defn validate-record
  [record type data-defs]
  (->> (data-defs type)
       all-valid-keys
       (select-keys record)
       validate))

(defn validate-persistent-record
  [record type data-defs]
  (select-keys record (persisted-attrs (data-defs type))))
