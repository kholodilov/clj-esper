(ns clj-esper.test.core
  (:use [clj-esper.core] :reload)
  (:use [clojure.test])
  (:import [com.espertech.esper.client EPServiceProviderManager]
           [com.espertech.esper.core.service EPServiceProviderImpl]))


(defevent TestEvent [a :int b :string])
(defevent OtherEvent [a :string])
(defstatement select-test "SELECT a, b FROM TestEvent")
(defstatement select-other "SELECT a FROM OtherEvent")

(deftest statements
  (with-esper service {:events #{TestEvent}}
    (attach-statement select-test)
    (is (= "select-test"
           (first (statement-names))))))

(deftest with-esper-uri
  (with-esper service {:uri "blah"}
    (is (instance? EPServiceProviderImpl
                   (EPServiceProviderManager/getProvider "blah")))
    (is (= service
           (EPServiceProviderManager/getProvider "blah")))))

(deftest events
  (is (= "TestEvent"
         (event-name TestEvent)))
  (is (= [:a :b]
         (sort (event-attribute-names TestEvent))))
  (is (= {:a :int :b :string}
         (event-attributes TestEvent)))
  (is (= {:a nil :b nil}
         (new-event TestEvent)))
  (is (= {:a 1 :b "hello"}
         (new-event TestEvent :a 1 :b "hello")))
  (is (= TestEvent
         (meta (new-event TestEvent))))
  (is (= {:a 1 :b "hello"}
         (new-event TestEvent :a "1" :b "hello"))))

(deftest coercion
  (is (= {:a (int 1)}
         (coerce-values {:a "1"}
                        {:a :int})))
  (is (= {:a "1234"}
         (coerce-values {:a 1234}
                        {:a :string}))))

(defn- handler
  [atom]
  (fn [x]
    (swap! atom concat x)))

(deftest esper-configuration
  (with-esper service {:events #{TestEvent}}
    (let [config (configuration service)]
      (is (not (nil? config)))
      (is (= "TestEvent"
             (-> (.getEventType config "TestEvent")
                 (.getName))))
      (is (some #{"a" "b"}
                (-> (.getEventType config "TestEvent")
                    (.getPropertyNames)
                    vec)))
      (is (= Integer
             (-> (.getEventType config "TestEvent")
                 (.getPropertyType "a"))))
      (is (= String
             (-> (.getEventType config "TestEvent")
                 (.getPropertyType "b")))))))

(deftest esper-handlers
  (let [result (atom [])]
    (with-esper service {:events #{TestEvent}}
      (attach-statement select-test (handler result))
      (is (= 0 (count @result)))
      (trigger-event (new-event TestEvent :a 1 :b "Hello"))
      (is (= 1 (count @result)))))
  (let [result (atom [])
        other-result (atom [])]
    (with-esper service {:events #{TestEvent OtherEvent}}
      (attach-statement select-test (handler result))
      (attach-statement select-other (handler other-result))
      (trigger-event (new-event TestEvent :a 1 :b "Hello"))
      (is (= 1 (count @result)))
      (is (= 0 (count @other-result)))
      (trigger-event (new-event OtherEvent :a "Hello"))
      (is (= 1 (count @other-result)))))
  (let [result (atom [])]
    (with-esper service {:events #{TestEvent}}
      (attach-statement select-test (handler result))
      (trigger-event (new-event TestEvent :a 1 :b "Hello"))
      (let [r (first @result)]
        (is (= 1 (:a r)))
        (is (= "Hello" (:b r))))))
  (let [result (atom [])]
    (with-esper service {:events #{TestEvent}}
      (attach-statement select-test (handler result) (handler result))
      (trigger-event (new-event TestEvent :a 1 :b "Hello"))
      (is (= 2 (count @result)))))
  (let [r (atom [])]
    (with-esper service {:events #{TestEvent OtherEvent}}
      (attach-statements [select-test select-other]
                         (handler r) (handler r))
      (trigger-event (new-event TestEvent :a 1 :b "Hello"))
      (trigger-event (new-event OtherEvent :a "Hello"))
      (is (= 4 (count @r))))))

(deftest pull-events-test
  (with-esper service {:events #{TestEvent}}
    (let [stmt (create-statement service "SELECT * FROM TestEvent.std:unique(a)")]
      (trigger-event service TestEvent {:a 1 :b "2"})
      (trigger-event service TestEvent {:a 2 :b "3"})
      (is (= [{:a 1 :b "2"} {:a 2 :b "3"}] (pull-events stmt))))))

(deftest old-events-handler-test
  (with-esper service {:events #{TestEvent}}
    (let [stmt (create-statement service "SELECT irstream * FROM TestEvent.std:unique(a)")
          new-events (atom [])
          old-events (atom [])
          listener (create-listener (handler new-events) (handler old-events))]
      (attach-listener stmt listener)
      (trigger-event service TestEvent {:a 1 :b "2"})
      (trigger-event service TestEvent {:a 1 :b "3"})
      (is (= [{:a 1 :b "2"} {:a 1 :b "3"}] @new-events))
      (is (= [{:a 1 :b "2"}] @old-events)))))

(deftest attach-detach-listener-test
  (with-esper service {:events #{TestEvent}}
    (let [stmt (create-statement service "SELECT * FROM TestEvent")
          result (atom [])
          listener (create-listener (handler result))]
      (attach-listener stmt listener)
      (trigger-event service TestEvent {:a 1 :b "2"})
      (detach-listener stmt listener)
      (trigger-event service TestEvent {:a 2 :b "3"})
      (is (= [{:a 1 :b "2"}] @result)))))

(deftest external-time-events-test
  (let [conf (create-configuration (xml-configuration) [])
        service (create-service "external-time-events-test" conf)]
      (send-current-time-event service 0)
      (is (= 0 (get-current-time service)))
      (send-current-time-event service 10000)
      (is (= 10000 (get-current-time service)))
    ))
