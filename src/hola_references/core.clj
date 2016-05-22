(ns hola-references.core
  (:import [java.util.concurrent Executors]
           [java.lang Runtime]))


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
;; unknown point in time, in a thread pool. The curret thread don't get blocked, it
;; gets return inmediatly.
;; They can be used for anything that does not require strict consistency for reads, like:
;; - Counters (e.g. message rates in event processing)
;; - Collections (e.g. recently processed events)
;; One handy use for agents is to serialize access to a resource, such as a file or
;; another I/O stream
;; Agents are great for high volume I/O tasks, or where the ordering of operations provides
;; a win in the high contentions scenarios.

(def errors-counter (agent 0))

errors-counter

(deref errors-counter)

;; Agents allow mutation as a result of an action. Actions are functions (with
;; optionally additional arguments) that are asynchronously applied to an Agent's
;; state and whose return value becomes the Agent's new state.
;; Each agent has a queue to hold actions the need to be performed on its value, each
;; action produces a new value for the agent to hold and pass to the subsequent action.
;; By their nature only on action at a time can be operating on a given agent.
;; Because actions are functions ther can also be MULTIMETHODS and therefore
;; actions are potentially POLYMORPHIC.

;; *** Can we apply diferents actions depending on what we recieve?
;; for instance, different paths from LiveOSC

(def song (agent []))
(send song conj "name")
(deref song)

(defn slow-conj [coll item]
  (Thread/sleep 10000)
  (conj coll item))

(send song slow-conj "year")
(deref song)

(def op (agent 0))
(defn slow-op [a f b]
  (Thread/sleep 2000)
  (f a b))

;; Actions sends to the action queue are performed in the order they where send, no matter
;; how long each one takes.

;; If we send these actions:
(send op slow-op + 1)
(send op * 2)
(send op slow-op + 3)
(send op * 2)
(send op slow-op + 10)
(send op * 2)
(deref op)

;; We'll get the same result as if we send thsese:
(send op + 1)
(send op slow-op * 2)
(send op + 3)
(send op slow-op * 2)
(send op + 10)
(send op slow-op * 2)
(deref op)

;; Pensaba que las acciones se ponían en la
;; cola de acciones y que los treads libres en la thread pool las iban pillando por orden,
;; pero una vez en la thread-pool eran procesadas por distintos threads, lo que llevaría a que
;; la operación más rápida se debería efectuar antes que la más lenta. Pero no es así.
;; Las acciones se efectúan en el orden que fueron lanzadas, independientemente de lo que tarden,
;; y sólo una a la vez. Todos los sends lanzados a un mismo agent son tratados por el mismo thread.
;; Even though the actions are executed in a thread-pool, actions for the same agent are never
;; executed concurrently. this is an excellent ordering guarantee that also extends a natural
;; coordination mechanism, due to its serial nature.


;; You can queue an action on any agent by using send or send-off
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

;;+++++++++++++++++++++++++++++++++
;;  Controlling I/= with an agent
;;+++++++++++++++++++++++++++++++++

;; We create a function dothreads! to help illustrate some points


(def thread-pool
  (Executors/newFixedThreadPool
    (+ 2 (.availableProcessors (Runtime/getRuntime)))))
;; Thread pool is 2 + available CPUs

(defn dothreads!
  [f & {thread-count :threads                 ;; number of threads
        exec-count :times                     ;; times to run the function
        :or {thread-count 1 exec-count 1}}]   ;; defaults
    (dotimes [t thread-count]
    (.submit thread-pool
             #(dotimes [_ exec-count] (f))))) ;; call the funtion

(dothreads! #(.print System/out "Hi ") :threads 2 :times 2)
;; Prints Hi 4 times in the console

(def log-agent (agent 0))
;; log-agent es el elemento que queremos que vaya actualizándose a medida que recibe
;; nuevos mensajes. Va contando los mensajes recibidos independientemente del thread
;; del que provengan. log-agent necesita por lo tanto actualizarse. Usamos un Agent.

(defn do-log [msg-id message]
  (println msg-id ":" message)
  (inc msg-id))
;; Esta es la función que vamos a usar para actualizar el estado del Agent.
;; El primer parámetro que recibe una función que luego se le va a pasar a un agent
;; através de send o send-off, es siempre el valor actual del agent, en este caso msg-id
;; Lo que hacemos con él es simplemente inc.


(defn do-step [channel message]
  (Thread/sleep 1)  ;; simulamos un trabajo
  (send-off log-agent do-log (str channel message)))
;; Cada vez que recibamos un mensaje esta es la función que va a actualizar nuestro agent.
;; No lo pasamos como argumento porque sólo tenemos un agent que actualizar y lo ponemos
;; dentro de la función directamente.
;; La función será invocada como (do-log @log-agent (str channel message))

(defn three-steps [channel]
  (do-step channel " ready to begin (step 0)")
  (do-step channel " warming up (step 1)")
  (do-step channel " really getting going down (step 2)")
  (do-step channel " done! (step 3)"))
;; Simulamos distintos mensajes que van a llegar a desde un channel determinado

(defn all-togheter-now []
  (dothreads! #(three-steps "alpha"))
  (dothreads! #(three-steps "beta"))
  (dothreads! #(three-steps "omega")))

(all-togheter-now)

;; 0 : beta ready to begin (step 0)
;; 1 : beta warming up (step 1)
;; 2 : alpha ready to begin (step 0)
;; 3 : beta really getting going down (step 2)
;; 4 : beta done! (step 3)
;; 5 : alpha warming up (step 1)
;; 6 : alpha really getting going down (step 2)
;; 7 : omega ready to begin (step 0)
;; 8 : alpha done! (step 3)
;; 9 : omega warming up (step 1)
;; 10 : omega really getting going down (step 2)
;; 11 : omega done! (step 3)



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
;; El resultado es una lista en la que vemos el mismo elemento, el mismo
;; atom, deferenciado en distintos momentos, por eso vemos 1, 2, 3, etc.


(time (reverse (map (fn [_] (swap! foo inc)) (range 10000))))
;; Como hemos visto antes, los cambios se van realizando uno por uno
;; y en orden.

(time (reverse (pmap (fn [_] (swap! foo inc)) (range 10000))))
;; En este caso usamos pmap, que paraleliza el task. Vemos que tarda más tiempo
;; que si se hiciera sin paralelizar, contra lo que cabría esperar.
;; Esto es porque son muchas tareas muy cortas y eso es malo para la paralelización,
;; que funciona mejor si hay pocas tareas pero largas. Una consecuencia de la
;; paralelización es que los resultados de los swaps ya no están ordenados.
;; Sin embargo, parece que el valor de @foo una vez que se han realizado todas
;; las operaciones es el mismo en los dos casos. Esto es porque si dos trheads han
;; empezado a la vez, sólo uno puede resultar exitoso, y el otro tiene que volver
;; a intentar. Por lo tanto no se pierde ningún paso.



(def boo (agent 0))
(map (fn [_] (send boo + 1)) (range 10000))
(deref boo)
;; El valor de @boo después de haber procesado todos los sends debería ser 10000
;; Sin embargo, el valor devuelto es 1024.
;; Creo que es porque map es lazy y deref no obliga a evaluar toda la lista
;; por lo que nos da el resultado solo de los send evaluados
;; Si obligamos a evaluar la lista que produce map, por ejemplo aplicando reverse,
;; el resultado entonces es el esperado.
(def boo (agent 0))
(reverse (map (fn [_] (send boo + 1)) (range 10000)))
(deref boo)

;; Al enviar una acción a un agent el thread en el que estamos no se bloquea, sino que sigue
;; corriendo. Si estamos enviando más de un send, todos se lanzan a un threadpool en el que se
;; quedarán esperando por orden a que los que van delante en la action queue se vayan ejecutando.




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

