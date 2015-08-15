(ns node-monitor.core-test
  (:require [clojure.test :refer :all]
            [node-monitor.core :refer :all]))



(deftest json-response-test
  (testing "Testing json-response handler"
    (let [entry {:a "aa" :b "bb"} ]
      (is (= 200 (:status (json-response entry))))

      (is (= 400 (:status (json-response 400 entry))))

      )))


(deftest test-config
  (testing "Test config"
    (let [host "1.2.3.4"
          test-entry {:name "X12" :host host}]

      (testing "Test empty config"
        (init-config)

        (is (empty? (:node-list @config)) "Should been empty")

        (is (= nil ((:node-list @config) host)) "Expected no entry ")
        )

      (testing "Create and remove host entry"
        (create-or-update-node-entry test-entry)

        (is (not= nil ((:node-list @config) host)) "Expected to find an entry")

        (remove-node-entry host)

        (is (= nil ((:node-list @config) host)) "Expected no entry ")

        (is (empty? (:node-list @config)) "Should been empty")

        )
      )
    )
  )
