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

;; what happens if two separate threads call swap!?
;; Is it possible for one of the swaps to get lost. The answer is no.
;; Swap! implements compare-and-set semantics, meaning it does the following internally:

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
;; actions are potentially POLYMORPHIC. (This sounds interesting. Come back to this later.)

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



;;+++++++++++++++++++++++++++++++++++++++++++++++++
;; Looking for differences between Atoms and Agents
;;+++++++++++++++++++++++++++++++++++++++++++++++++


(def foo (atom 0))#'hola-references.core/foo

;; This has nothing to do with atoms or agents. It´s just to
;; understand the difference between the two expressions,
;; because it´s going to be used later.
(map inc (range 10))
;; inc is applied to each element of the list generated by (range 10): (1 2 3 4 5 6 7 8 9 10)
(map (fn [_] (inc 0)) (range 10))
;; (inc 0) is applied once for each element in (range 10): (1 1 1 1 1 1 1 1 1 1)


(map (fn [_] (swap! foo inc)) (range 10))
;; We apply (swap! foo inc) to each element in (range 10)
;; Because of atom do its work synchronously, it blocks the current thread
;; of execution until the computation of the first swap! is done, which result
;; is 1. Then it goes on and computes the second swap! over the current value of the
;; atom, which is 1, and the result is 2. And so on.
;; The result: (1 2 3 4 5 6 7 8 9 10) is a list where we see the same atom
;; defered at differente points in time.

(reset! foo 0)
(map (fn [_] (swap! foo inc)) (range 100000))
;; Changes are made one by one and in order.

(reset! foo 0)
(time (map (fn [_] (swap! foo inc)) (range 100000)))
(reset! foo 0)
(time (pmap (fn [_] (swap! foo inc)) (range 100000)))
;; In this case we use pmap, that parallelizes the task.
;; We can see that, contrary to what wee could expect, it takes longer
;; when we use pmap. This is because pmap doesn´t work well with many short tasks.
;; It is best suited for less but longer tasks.
;; A consequence of using pmap is that the swap! results are no longer ordered.
;; Yet the result of (deref foo) for the pmap option should be the same as for
;; the map option.
;; And it is, but to my surprise, it is not what I expected, that is 100000
(deref foo)
;; The result of deref foo in both cases (stating whith (atom 0)) is 1024.

;; I try the same operations with a smaller range.
(reset! foo 0)
(map (fn [_] (swap! foo inc)) (range 100))
(deref foo)
;; And the result is 100, the expected one.

;; I try a few more times with different numbers and I check that the unexpeted behaviour
;; starts when the range´s argument is bigger than 1024.

;; After thinking what can possibly be going on here, I came to the conclusion that it is
;; because map is lazy. So when the argument is bigger than 1024, map is no longer applied
;; and therefore the atom state is no longer changed.
;; (deref foo) does not force the complete evaluation of map
;; If we use a function that does force the complete evaluation, e.g. reverse, the result of
;; (deref foo) will be correct.
(reset! foo 0)
(reverse (map (fn [_] (swap! foo inc)) (range 100000)))
(deref foo)


(def boo (agent 0))
(map (fn [_] (send boo + 1)) (range 10000))



(def boo (agent 0))
(map (fn [_] (send boo + 1)) (range 10000))
(deref boo)

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

