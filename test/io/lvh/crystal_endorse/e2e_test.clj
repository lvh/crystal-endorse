(ns io.lvh.crystal-endorse.e2e-test
  (:require [clojure.test :refer :all]
            [io.lvh.crystal-endorse :as ce]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream PrintStream ByteArrayInputStream]))

;; Helper function to capture stdout
(defn with-out-str-and-value [f]
  (let [out (ByteArrayOutputStream.)
        old-out System/out]
    (try
      (System/setOut (PrintStream. out))
      (let [result (f)]
        {:result result
         :out-str (str out)})
      (finally
        (System/setOut old-out)))))

;; Helper function to provide stdin
(defn with-in-str-and-result [s f]
  (let [old-in System/in]
    (try
      (System/setIn (ByteArrayInputStream. (.getBytes s)))
      (f)
      (finally
        (System/setIn old-in)))))

;; Mock function to replace the shell call
(defn mock-shell [& args]
  (let [options (first args)
        all-args (vec args)
        cmd (get all-args 1)]
    (cond
      ;; Mock ssh-keygen sign
      (and (= "ssh-keygen" cmd) (= "-Y" (get all-args 2)) (= "sign" (get all-args 3)))
      {:exit 0
       :out "SSH-SIGNATURE\nMock signature line 1\nMock signature line 2"
       :err ""}
      
      ;; Mock ssh-keygen verify
      (and (= "ssh-keygen" cmd) (= "-Y" (get all-args 2)) (= "verify" (get all-args 3)))
      {:exit 0
       :out "Good signature"
       :err ""}
      
      :else
      {:exit 1
       :out ""
       :err "Unknown command"})))

(deftest main-sign-test
  (testing "Sign function with file"
    (with-redefs [io.lvh.crystal-endorse/shell mock-shell]
      (let [temp-file (str (fs/create-file (fs/path (fs/temp-dir) (str "crystal-endorse-test-" (System/currentTimeMillis)))))
            _ (spit temp-file "Test message for signing")
            result (ce/sign :message (slurp temp-file) :key "/tmp/test-key")]
        (is (str/includes? result ce/begin-message))
        (is (str/includes? result "Test message for signing"))
        (is (str/includes? result ce/begin-signature))
        (is (str/includes? result "Mock signature line"))
        (fs/delete temp-file)))))

(deftest main-verify-test
  (testing "Verify function"
    (with-redefs [io.lvh.crystal-endorse/shell mock-shell]
      (let [input (str ce/begin-message "\n\nTest message\n" 
                       ce/begin-signature "\nMock signature\n" ce/end-signature)
            result (ce/verify-signature (str/split-lines input))]
        (is result)))))

(deftest main-extract-message-test
  (testing "Extract message function"
    (with-redefs [io.lvh.crystal-endorse/shell mock-shell]
      (let [input (str ce/begin-message "\n\nTest message\n" 
                       ce/begin-signature "\nMock signature\n" ce/end-signature)
            result (ce/extract-message (str/split-lines input))]
        (is (str/includes? result "Test message"))
        (is (not (str/includes? result "Mock signature")))))))

(deftest main-extract-signature-test
  (testing "Extract signature function"
    (with-redefs [io.lvh.crystal-endorse/shell mock-shell]
      (let [input (str ce/begin-message "\n\nTest message\n" 
                       ce/begin-signature "\nMock signature\n" ce/end-signature)
            result (ce/extract-signature (str/split-lines input))]
        (is (str/includes? result "Mock signature"))
        (is (not (str/includes? result "Test message")))))))