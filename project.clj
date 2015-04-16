(defproject kholodilov/clj-esper "1.0.3-SNAPSHOT" 
  :min-lein-version "2.0.0"
  :repositories [["codehaus" "http://repository.codehaus.org"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.espertech/esper "5.2.0" :exclusions [log4j]]]
  :description "Simple Esper wrapper for Clojure")
