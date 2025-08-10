(ns io.lvh.crystal-endorse.edge-cases-test
  (:require [clojure.test :refer :all]
            [io.lvh.crystal-endorse :as ce]
            [clojure.string :as str]
            [babashka.fs :as fs])
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
  {:exit 0
   :out "SSH-SIGNATURE\nMock signature"
   :err ""})

(deftest malformed-input-test
  (testing "Malformed clearsign message without BEGIN PGP SIGNED MESSAGE"
    (let [malformed (str "This is not a valid clearsigned message\n"
                         ce/begin-signature "\nSignature\n" ce/end-signature)]
      (is (nil? (ce/split-clearsign (str/split-lines malformed))))))
  
  (testing "Malformed clearsign message without BEGIN PGP SIGNATURE"
    (let [malformed (str ce/begin-message "\n\nMessage\nNo signature marker")]
      (is (nil? (ce/split-clearsign (str/split-lines malformed))))))
  
  (testing "Malformed clearsign message without END PGP SIGNATURE"
    (let [malformed (str ce/begin-message "\n\nMessage\n" ce/begin-signature "\nSignature without end")]
      (is (nil? (ce/split-clearsign (str/split-lines malformed))))))
  
  (testing "Extract functions return nil for malformed input"
    (let [malformed "Not a clearsigned message"]
      (is (nil? (ce/extract-message (str/split-lines malformed))))
      (is (nil? (ce/extract-signature (str/split-lines malformed)))))))

(deftest empty-input-test
  (testing "Empty input"
    (with-redefs [io.lvh.crystal-endorse/shell mock-shell]
      (let [empty-input ""
            output (with-in-str-and-result empty-input
                     #(with-out-str-and-value
                        (fn [] (ce/sign))))]
        ;; Should still generate output with empty message but valid signature
        (is (str/includes? output ce/begin-message))
        (is (str/includes? output ce/begin-signature))
        (is (str/includes? output "Mock signature"))))))

(deftest unicode-content-test
  (testing "Unicode content in message"
    (with-redefs [io.lvh.crystal-endorse/shell mock-shell]
      (let [unicode-msg "Unicode test: こんにちは 你好 مرحبا Привет"
            result (ce/sign :message unicode-msg)]
        (is (str/includes? result unicode-msg))
        (is (str/includes? result ce/begin-signature)))))
  
  (testing "Unicode content in file paths"
    (with-redefs [io.lvh.crystal-endorse/shell mock-shell
                  babashka.fs/exists? (constantly true)]
      (let [result (ce/sign :key "/path/with/unicode/こんにちは/key" :message "Test")]
        (is (str/includes? result ce/begin-message))
        (is (str/includes? result ce/begin-signature))))))

(deftest fossil-user-card-edge-cases-test
  (testing "No user card in fossil artifact"
    (is (thrown? AssertionError (ce/get-fossil-user-card ["A abc123" "B def456" "Z ghi789"]))))
  
  (testing "Multiple user cards in fossil artifact"
    (is (thrown? AssertionError (ce/get-fossil-user-card ["U user1" "A abc123" "U user2" "Z ghi789"])))))

(deftest verify-signature-edge-cases-test
  (testing "Verify with empty message"
    (with-redefs [io.lvh.crystal-endorse/shell mock-shell]
      (let [empty-msg-clearsign (str ce/begin-message "\n\n" 
                                     ce/begin-signature "\nSignature\n" ce/end-signature)]
        (is (ce/verify-signature (str/split-lines empty-msg-clearsign))))))
  
  (testing "Verify with empty signature"
    (with-redefs [io.lvh.crystal-endorse/shell (fn [& _] {:exit 1 :err "Invalid signature"})]
      (let [empty-sig-clearsign (str ce/begin-message "\n\nMessage\n" 
                                     ce/begin-signature "\n" ce/end-signature)]
        (is (not (ce/verify-signature (str/split-lines empty-sig-clearsign))))))))