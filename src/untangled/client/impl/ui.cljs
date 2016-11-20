(ns untangled.client.impl.ui
  (:require
    [clojure.spec :as s]
    [goog.dom :as gdom]
    [untangled.client.impl.util :as utl]))

(defn createNode [[kind {:as x :keys [ele attr kids]}]]
  (case kind
    :str x
    :vec (apply gdom/createDom (name ele) (clj->js attr) kids)))

(s/def ::hiccup*
  (s/or :str string?
        :vec (s/and vector?
               (s/cat :ele keyword?
                 :attr (s/? map?)
                 :kids (s/* ::hiccup*)))))

(s/def ::hiccup
  (s/or :vec
        (s/and vector?
          (s/cat :ele keyword?
            :attr (s/? map?)
            :kids (s/* ::hiccup*)))))

(defn html* [[kind node :as x]]
  (case kind
    :str node
    :vec (-> x
           (update-in [1 :kids] #(mapv html* %))
           createNode)))

(defn html [x]
  (html* (utl/conform! ::hiccup x)))
