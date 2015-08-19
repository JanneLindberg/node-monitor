(ns node-monitor.core-test
  (:require [clojure.test :refer :all]
            [node-monitor.core :refer :all]))



(deftest json-response-test
  (testing "Testing json-response handler"
    (let [entry {:a "aa" :b "bb"} ]
      (is (= 200 (:status (json-response 200 entry))))

      (is (= 200 (:status (json-response 200))))

      (is (not (empty? (:body (json-response 200 entry)))))

      (is (empty? (:body (json-response 200))))

      (is (= 400 (:status (json-response 400 entry))))

      )))


(deftest test-config
  (testing "Test config"
    (let [host "1.2.3.4"
          test-entry {:name "X12" :host host}]

      (testing "Test empty config"
        (init-config)

        (is (empty? (:nodes @config)) "Should been empty")

        (is (= nil ((:nodes @config) host)) "Expected no entry ")
        )

      (testing "Create and remove host entry"
        (create-or-update-node-entry test-entry)

        (is (not= nil ((:nodes @config) host)) "Expected to find an entry")

        (remove-node-entry host)

        (is (= nil ((:nodes @config) host)) "Expected no entry ")

        (is (empty? (:nodes @config)) "Should been empty")

        )
      )
    )
  )


(deftest test-node-status-change

  (testing "Test config"

    (let [host "1.2.3.4"
          t1 {:host host :active true }
          t2 {:host host :active false }]

      (is (empty? ((get-lost-nodes) host)))

      (node-status-changed t2)

      (is (not (empty? ((get-lost-nodes) host))))

      (node-status-changed t1)

      (is (empty? ((get-lost-nodes) host)))

)))

