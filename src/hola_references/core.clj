(ns hola-references.core)

;; +++++++++++++++++++++++++++++++++++++++++++
;; Shifting computation through Time and Space
;; +++++++++++++++++++++++++++++++++++++++++++

;; Clojure provide delays, futures and promises to encapsulate
;; discrete use cases for controlling WHEN and HOW computations
;; are preformed.

;;++++++++++++++++++++
;;     DELAYS
;;++++++++++++++++++++








;;++++++++++++++++++++
;;       ATOMS
;;++++++++++++++++++++

;;Atoms are for synchronous, uncoordinated access to a single identity.

;;Example from http://clojure-doc.org/articles/language/concurrency_and_parallelism.html

(def currently-connected (atom []))

(deref currently-connected)

currently-connected

;; locals can be atoms too.
(let [xs (atom [])]
  @xs)

(swap! currently-connected conj "chatty-joe")
(reset! currently-connected [])
;; the rule of thumb is, "start with an atom, then see".


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



;;++++++++++++++++++++
;;       AGENTS
;;++++++++++++++++++++
;; Agents are for asynchronous, uncoordinated access to a single entity.
;; Agents are references that are updated asynchronously: updates happen at a later,
;; unknown point in time, in a thread pool.
;; They can be used for anything that does not require strict consistency for reads, like:
;; - Counters (e.g. message rates in event processing)
;; - Collections (e.g. recently processed events)

(def errors-counter (agent 0))

errors-counter

(deref errors-counter)

;; send and send-off are similar and are used to mutate the agent.
;; send uses a fixed-size thread pool that is configured to not exceed the parallelizability
;; of the current hardware. THUS, send must never be used for actions that might perform I/O
;; or other blocking operations
;; senf-off uses a growing (or unbounded) thread pool, which allows any number of
;; potentially blocking, non-CPU-bound actions to be evaluated concurrently.

(send errors-counter inc)

(deref errors-counter)

(send errors-counter + 10)
;; the + function will be invoked as (+ @errors-counter 10)

(deref errors-counter)

;; Agents allow mutation as a result of an action. Actions are functions (with
;; optionally additional arguments) that are asynchronously applied to an Agent's
;; state and whose return value becomes the Agent's new state. Because actions are
;; functions ther can also be MULTIMETHODS and therefore actions are potentially
;; POLYMORPHIC.

;; *** Can we apply diferents actions depending on what we recieve?
;; for instance, different paths from LiveOSC



;;+++++++++++++++++++++++++++++++++
;; Diferencias entre Atoms y Agents
;;+++++++++++++++++++++++++++++++++


(def foo (atom 0))

;; esto no tiene que ver nada con atoms o agents, pero es para
;; entender la diferencia, ya que lo vamos a aplicar después.
(map inc (range 10))
;; inc se aplica a cada elemento de la lista generada por (range 10)
(map (fn [_] (inc 0)) (range 10))
;; se aplica (inc 0) por cada elemento de (range 10)


(map (fn [_] (swap! foo inc)) (range 10))
;; Aplicamos (swap! foo inc) por cada elemento de (range 10)
;; como atom es sincrónico, bloquea este thread hasta que acaba
;; de calcular el primer swap! cuyo resultado es 1. Luego continua
;; el thread para calcular el segundo elemento. Como en ese momento
;; el atom ya ha cambiado, el resultado es 2.


(time (reverse (map (fn [_] (swap! foo inc)) (range 10000))))
;; Como hemos visto antes, los cambios se van realizando uno por uno
;; y en orden.

(time (reverse (pmap (fn [_] (swap! foo inc)) (range 10000))))
;; En este caso usamos pmap, que paraleliza el task. Vemos que tarda más tiempo
;; que si se hiciera sin paralelizar. Esto es porque son muchas tareas muy cortas
;; y eso es malo para la paralelización, que funciona mejor si hay pocas tareas
;; pero largas. Una consecuencia de la paralelización es que los resultados de los
;; swaps ya no están ordenados. Sin embargo, parece que el valor de @foo es el mismo
;; en los dos casos. Esto es porque si dos trheads han empezado a la vez, sólo uno puede
;; resultar, y el otro tiene que volver a intentar. Por lo tanto no se pierde ningún paso.





(def too (agent 1))
(map (fn [_] (send-off too / 0)) (range 1025))
(deref boo)
;; Si hago estos pasos en orden, obviamente @boo es 10 si ha pasado suficiente tiempo
;; para que todos los send se hayan procesado
;; Si el valor que le paso a range es bajo, no pasa nada raro
;; pero si es mayor de 1024, el valor de @boo nunca es mayor de 1024.
;; ¿A qué se debe? ¿Es debido a algún límite de los mensajes que se pueden poner
;; en cola o algo así? ¿O a un límite de threads?
;; Si uno send-off pasa lo mismo.


;; Al enviar una acción a un agent el thread en el que estamos no se bloques, sino que sigue
;; corriendo. Si estamos enviando más de un send, todos se lanzan a un threadpool en el que se
;; quedarán esperando por orden a que los que van delante en la action queue se vayan ejecutando.

(def lista  (map (fn [_] (send boo inc)) (range 10)))
;;(reverse lista)
(map deref lista)
(deref boo)




;;++++++++++++++++++++
(def foo-agent (agent 0))
(def counter (atom 0))

(defn slow-inc [n]
  (swap! counter inc)
  (Thread/sleep 2000)
  (inc n))

(do
  (send foo-agent slow-inc)
  (send foo-agent slow-inc)
  (println "foo-agent: " @foo-agent)
  (println "counter: " @counter)
  (Thread/sleep 2500)
  (println "foo-agent: " @foo-agent)
  (println "counter: " @counter)
  (Thread/sleep 2500)
  (println "foo-agent: " @foo-agent)
  (println "counter: " @counter))
;;++++++++++++++++++++

