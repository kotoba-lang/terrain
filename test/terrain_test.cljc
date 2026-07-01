(ns terrain-test
  (:require [clojure.test :refer [deftest is testing]]
            [terrain]))
(deftest namespace-loads
  (testing "the restored CLJC namespace loads"
    (is (some? terrain))))
