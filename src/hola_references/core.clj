(ns hola-references.core)

;;++++++++++++++++++++
;;       ATOMS
;;++++++++++++++++++++

;;Atoms are for synchronous, uncoordinated access to a single identity.

;;Example from "Clojure for the Brave and the True"

(def fred (atom {:cuddle-hunger-level 0
                 :percent-deteriorated 0}))

;;This atom refers at this moment to the value {:cuddle-hunger-level 0 :percent-deteriorated 0}

(println @fred)

;; Unlike delays, futures and promises, dereferencing a reference type will never block.

;; When you dereference futures, delays, and promises, it’s like you’re saying “I need a value now, and I will wait until I get it”
;; However, when you dereference a reference type, it’s like you’re saying “give me the value I’m currently referring to,”
;; so it makes sense that the operation doesn’t block, because it doesn’t have to wait for anything.

(defn add-to-fred [add-values]
  (swap! fred (fn [current-state]
                (merge-with + current-state add-values))))

(add-to-fred {:cuddle-hunger-level 1 :percent-deteriorated 3})

;;We can also use update-in
(update-in {:a {:a1 {:a2 2}} :b 3} [:a :a1] (fn [mapa] (assoc mapa :b2 3)))

(defn add-to-cuddle-hunger-level [value]
  (swap! fred update-in [:cuddle-hunger-level] + value))

(add-to-cuddle-hunger-level 4)

;;By using atoms, you can retain past state:
(let [num (atom 1)
      s1 @num
      s2 (swap! num inc)]
  (swap! num inc)
  (println "State 1:" s1)
  (println "State 2:" s2)
  (println "Current state:" @num))

;;what happens if two separate threads call swap!?
;; Is it possible for one of the swaps to get lost. The answer is no.
;;Swap! implements compare-and-set semantics, meaning it does the following internally:

;; - It reads the current state of the atom.
;; - It then applies the update function to that state.
;; - Next, it checks whether the value it read in step 1 is identical to the atom’s current value.
;; - If it is, then swap! updates the atom to refer to the result of step 2.
;; - If it isn’t, then swap! retries, going through the process again with step 1.


;; Atoms update happen synchronously, that means they will block their thread
;; until completed.

;; sometimes we want to update an atom without checking its current value
(reset! fred {:cuddle-hunger-level 0 :percent-deteriorated 0})
(println @fred)


;; WATCHES
;; A watch is a function that takes four arguments: a key, the reference being watched,
;; its previous state, and its new state. You can register any number of watches with a reference type.






















