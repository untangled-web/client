(ns untangled.client.augmentation
  (:require
    [untangled.client.impl.util :as utl]
    [clojure.string :as str]
    [clojure.spec.alpha :as s]))

(defmulti defui-augmentation
   "Multimethod for defining augments for use in `untangled.client.ui/defui`.
    * `ctx` contains various (& in flux) information about the context in which
    the augment is being run (eg: :defui/ui-name, :env/cljs?, :defui/loc).
    * `ast` contains the conformed methods of the defui, and is the subject and focus of your transformations.
    * `params` contains the parameters the user of your augment has passed to you
    when using the augment to make their defui.
    eg: `(defui MyComp [(:your/augment {:fake :params})] ...)`"
   {:arglists '([ctx ast params])}
  (fn [ctx _ _] (:augment/dispatch ctx)))

(defmulti defui-augmentation-group (fn [{:keys [aug]}] aug))
(defmethod defui-augmentation-group :default [& _] nil)

(defn- my-group-by [f coll]
  (into {} (map (fn [[k v]]
                  (assert (= 1 (count v))
                    (str "Cannot implement " k " more than once!"))
                  [(name k) (first v)]) (group-by f coll))))
(defn group-impls [x]
  (-> x
    (update :impls (partial mapv (fn [x] (update x :methods (partial my-group-by :name)))))
    (update :impls (partial my-group-by :protocol))))
(defn ungroup-impls [x]
  (-> x
    (update :impls vals)
    (update :impls (partial mapv (fn [x] (update x :methods vals))))))

(s/def ::method
  (s/cat :name symbol?
    :param-list (s/coll-of symbol? :into [] :kind vector?)
    :body (s/+ utl/TRUE)))
(s/def ::impls
  (s/cat :static (s/? '#{static})
    :protocol symbol?
    :methods (s/+ (s/spec ::method))))
(s/def ::augments.vector
  (s/coll-of (s/or :kw keyword? :sym symbol?
                   :call (s/cat :aug (s/or :kw keyword? :sym symbol?)
                           :params map?))
    :into [] :kind vector?))
(s/def ::dev ::augments.vector)
(s/def ::prod ::augments.vector)
(s/def ::always ::augments.vector)
(s/def ::augments
  (s/or
    :map (s/and map? (s/keys :opt-un [::dev ::prod ::always]))
    :vector ::augments.vector))
(s/def ::defui-name symbol?)
(s/def ::defui
  (s/and (s/cat
           :defui-name ::defui-name
           :augments (s/? ::augments)
           :impls (s/+ ::impls))
    (s/conformer
      group-impls
      ungroup-impls)))

(def ^:private defui-augment-mode
  (let [allowed-modes {"prod" :prod, "dev" :dev}
        ?mode (str/lower-case
                (or (System/getenv "DEFUI_AUGMENT_MODE")
                    (System/getProperty "DEFUI_AUGMENT_MODE")
                    "prod"))]
    (or (get allowed-modes ?mode)
        (throw (ex-info "Invalid DEFUI_AUGMENT_MODE, should be 'prod' or 'dev'"
                 {:invalid-mode ?mode, :allowed-modes (set (keys allowed-modes))})))))
(.println System/out (str "INITIALIZED DEFUI_AUGMENT_MODE TO: " defui-augment-mode))

(defn- parse [[aug-type aug]]
  (case aug-type
    (:kw :sym) {:aug aug}
    :call (update aug :aug (comp :aug parse))))

(declare parse-augments)

(defn- expand-augment [augment]
  (if-let [[aug-group cb] (defui-augmentation-group augment)]
    (cb (parse-augments (utl/conform! ::augments aug-group)))
    [augment]))

(defn parse-augments "WARNING: FOR INTERNAL USE" [[augs-type augs]]
  (case augs-type
    :vector (into [] (comp (map parse) (mapcat expand-augment)) augs)
    :map (parse-augments [:vector (apply concat (vals (select-keys augs [:always defui-augment-mode])))])))

;;==================== AUGMENT BUILDER HELPERS ====================

(defn add-defui-augmentation-group
  "Used for defining a one to many alias for augments,
   so that you can bundle up various augments under a single augment.
   Has the same augment syntax as `untangled.client.ui/defui`,
   but see ::augments for exact and up to date syntax.

   Example: `:untangled.client.ui/BuiltIns` in `untangled.client.impl.built-in-augments`"
  [group-dispatch build-augs]
  (defmethod defui-augmentation-group group-dispatch [augment]
    [(build-augs augment)
     (partial mapv
       (fn [{:as <> :keys [params aug]}]
         (cond-> <> params
           (update :params merge (get (:params augment) aug)))))]))

(s/def ::inject-augment
  (s/cat
    :ast utl/TRUE
    :static (s/? '#{static})
    :protocol symbol?
    :method symbol?
    :body utl/TRUE))

(defn inject-augment
  "EXPERIMENTAL, may change to some sort of def-augment-injection macro

   For use in a defui-augmentation for injecting a method under a protocol.

   WARNING: Does not currently check that the method does not exist,
   so this may override the targeted method if it existed before.

   EXAMPLE: `:untangled.client.ui/DerefFactory` in `untangled.client.impl.built-in-augments`"
  [& args]
  (let [{:keys [ast static protocol method body]}
        (utl/conform! ::inject-augment args)]
    ;;TODO: Check protocol & method dont already exist
    (update-in ast [:impls (str protocol)]
      #(-> %
         (assoc
           :protocol protocol)
         (cond-> static
           (assoc :static 'static))
         (assoc-in [:methods (str method)]
           {:name method
            :param-list (second body)
            :body [(last body)]})))))

(s/def ::wrap-augment
  (s/cat
    :ast utl/TRUE
    :protocol symbol?
    :method symbol?
    :wrapper fn?))

(defn wrap-augment
  "EXPERIMENTAL: May change to some sort of def-augment-behavior macro.

   For use in `defui-augmentation` for wrapping an existing method with a behavior.
   Is run at compile time, and can be used to transform the method body, or simply add a run time function call.

   WARNING: Does not check that the method (& protocol) existed,
   so it may crash unexpectedly if the method is not found until fixed
   (if so do tell us on the clojurians slack channel #untangled, and/or make a github issue).

   EXAMPLE: `:untangled.client.ui/WithExclamation` in `untangled.client.impl.built-in-augments`"
  [& args]
  (let [{:keys [ast protocol method wrapper]}
        (utl/conform! ::wrap-augment args)]
    ;;TODO: Check protocol & method already exist
    (update-in ast [:impls (str protocol) :methods (str method)]
      (fn [{:as method :keys [body param-list]}]
        (assoc method :body
          (conj (vec (butlast body))
                (wrapper param-list (last body))))))))
