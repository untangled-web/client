(ns untangled.events)

(defn delete-key?
  "Return true if a DOM event was the delete key."
  [evt]
  (= 8 (.-keyCode evt)))

(defn tab-key?
  "Return true if a DOM event was the tab key."
  [evt]
  (= 9 (.-keyCode evt)))

(defn enter-key?
  "Return true if a DOM event was the enter key."
  [evt]
  (= 13 (.-keyCode evt)))

(defn escape-key?
  "Return true if a DOM event was the escape key."
  [evt]
  (= 27 (.-keyCode evt)))

(defn space-key?
  "Return true if a DOM event was the spacebar key."
  [evt]
  (= 32 (.-keyCode evt)))

(defn page-up-key?
  "Return true if a DOM event was the page up key."
  [evt]
  (= 33 (.-keyCode evt)))

(defn page-down-key?
  "Return true if a DOM event was the page down key."
  [evt]
  (= 34 (.-keyCode evt)))
  
(defn left-key?
  "Return true if a DOM event was the left key."
  [evt]
  (= 37 (.-keyCode evt)))

(defn up-key?
  "Return true if a DOM event was the up key."
  [evt]
  (= 38 (.-keyCode evt)))

(defn right-key?
  "Return true if a DOM event was the right key."
  [evt]
  (= 39 (.-keyCode evt)))

(defn down-key?
  "Return true if a DOM event was the down key."
  [evt]
  (= 40 (.-keyCode evt)))
