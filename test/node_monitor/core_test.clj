(ns node-monitor.core-test
  (:require [clojure.test :refer :all]
            [node-monitor.core :refer :all]))


(deftest entry-name-test
  (testing "Test fetching the entry name from an configuration entry"

    (is (= "10.11.12.1" (entry-name {:xname "aname" :ip "10.11.12.1"})))

    (is (= "aname" (entry-name {:name "aname" :ip "10.11.12.1"})))

    (is (= nil (entry-name {:xname "aname" :xip "10.11.12.1"})))

))

