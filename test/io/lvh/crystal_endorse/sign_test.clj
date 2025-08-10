(ns io.lvh.crystal-endorse.sign-test
  (:require [clojure.test :refer :all]
            [io.lvh.crystal-endorse :as ce]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayInputStream]))

;; Mock function to replace the shell call in the sign function
;; This will be used with with-redefs
(defn mock-shell [& args]
  (let [options (first args)]
    (if (= :string (:out options))
      {:exit 0
       :out "SSH-SIGNATURE\nMock signature line 1\nMock signature line 2"
       :err ""}
      {:exit 0})))

(deftest sign-with-string-message-test
  (testing "Sign function with string message"
    (with-redefs [io.lvh.crystal-endorse/shell mock-shell]
      (let [message "Test message\nLine 2"
            result (ce/sign :message message)]
        (is (str/includes? result ce/begin-message))
        (is (str/includes? result "Test message"))
        (is (str/includes? result "Line 2"))
        (is (str/includes? result ce/begin-signature))
        (is (str/includes? result "Mock signature line 1"))
        (is (str/includes? result ce/end-signature))))))

(deftest sign-with-input-stream-test
  (testing "Sign function with input stream"
    (with-redefs [io.lvh.crystal-endorse/shell mock-shell
                  clojure.java.io/reader (fn [_] (java.io.BufferedReader. (java.io.StringReader. "Input stream message")))]
      (let [result (ce/sign :allow-stdin true)]
        (is (str/includes? result ce/begin-message))
        (is (str/includes? result "Input stream message"))
        (is (str/includes? result ce/begin-signature))
        (is (str/includes? result "Mock signature line"))
        (is (str/includes? result ce/end-signature))))))

(deftest sign-with-custom-key-test
  (testing "Sign function with custom key path"
    (with-redefs [io.lvh.crystal-endorse/shell (fn [& args]
                                           (let [args (drop 3 args) ; Skip the options, ssh-keygen, and -Y arguments
                                                 key-arg-pos (.indexOf args "--key")]
                                             (if (and (>= key-arg-pos 0) 
                                                      (= (nth args (inc key-arg-pos)) "/custom/key/path"))
                                               {:exit 0
                                                :out "SSH-SIGNATURE\nCustom key signature"
                                                :err ""}
                                               (throw (Exception. "Expected custom key path")))))]
      (let [result (ce/sign :key "/custom/key/path" :message "Test")]
        (is (str/includes? result "Custom key signature"))))))

(deftest sign-error-handling-test
  (testing "Sign function handles errors"
    (with-redefs [io.lvh.crystal-endorse/shell (fn [& _] 
                                           (throw (Exception. "Simulated ssh-keygen error")))]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo 
                           #"SSH signing failed" 
                           (ce/sign :message "Test")))
      
      ;; Test when no message source is available
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                           #"No message source available"
                           (ce/sign :allow-stdin false))))))

(deftest sign-without-armor-test
  (testing "Sign function without armor (clearsign)"
    (with-redefs [io.lvh.crystal-endorse/shell mock-shell]
      (let [result (ce/sign :message "Test message" :armor false)]
        (is (= "SSH-SIGNATURE\nMock signature line 1\nMock signature line 2" result))
        (is (not (str/includes? result ce/begin-message)))
        (is (not (str/includes? result ce/end-signature)))))))