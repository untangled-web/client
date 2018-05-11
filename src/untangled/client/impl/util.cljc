(ns untangled.client.impl.util
  (:require
    [clojure.pprint :refer [pprint]]
    [om.next :as om]
    #?(:clj
      [clojure.spec.alpha :as s])
    #?(:clj
      [clojure.spec.gen.alpha :as gen]))
  #?(:clj
     (:import [clojure.lang Atom])))

(defn atom? [a] (instance? Atom a))

(defn deep-merge [& xs]
  "Merges nested maps without overwriting existing keys."
  (if (every? map? xs)
    (apply merge-with deep-merge xs)
    (last xs)))

(defn log-app-state
  "Helper for logging the app-state. Pass in an untangled application atom and either top-level keys, data-paths
   (like get-in), or both."
  [app-atom & keys-and-paths]
  (try
    (let [app-state (om/app-state (:reconciler @app-atom))]
      (pprint
        (letfn [(make-path [location]
                  (if (sequential? location) location [location]))
                (process-location [acc location]
                  (let [path (make-path location)]
                    (assoc-in acc path (get-in @app-state path))))]

          (condp = (count keys-and-paths)
            0 @app-state
            1 (get-in @app-state (make-path (first keys-and-paths)))
            (reduce process-location {} keys-and-paths)))))
    (catch #?(:cljs js/Error :clj Exception) e
      (throw (ex-info "untangled.client.impl.util/log-app-state expects an atom with an untangled client" {})))))

#?(:clj
   (defn dbg [& args]
     (.println System/out (apply str (interpose " " args)))))

#?(:clj
   (defn conform! [spec x]
     (let [rt (s/conform spec x)]
       (when (s/invalid? rt)
         (throw (ex-info (s/explain-str spec x)
                         (s/explain-data spec x))))
       rt)))

#?(:clj
   (def TRUE (s/with-gen (constantly true) gen/int)))
