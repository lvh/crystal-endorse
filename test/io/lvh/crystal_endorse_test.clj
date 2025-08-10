(ns io.lvh.crystal-endorse-test
  (:require [clojure.test :refer :all]
            [io.lvh.crystal-endorse :refer :all]
            [clojure.string :as str]))

(def sample-clearsign
  (str/join "\n" 
            ["-----BEGIN PGP SIGNED MESSAGE-----"
             ""
             "This is a test message"
             "With multiple lines"
             "-----BEGIN PGP SIGNATURE-----"
             ""
             "iHUEARYIAB0WIQQoEk+2K+TQBpQKHaOT97wORLZN/QUCYgdGzQAKCRCT97wORLZN"
             "/UWUAP9pN1kD5A0TuW8lFwvAOXMge4EGKV7r9kNKjL2RAQi2swD9ERvTQ4gdwGKj"
             "WRRXUOlXKL8fdGQA2G7eyt5SzQkEWQs="
             "-----END PGP SIGNATURE-----"]))

(def sample-message-lines ["This is a test message" "With multiple lines"])
(def sample-signature-lines ["" 
                           "iHUEARYIAB0WIQQoEk+2K+TQBpQKHaOT97wORLZN/QUCYgdGzQAKCRCT97wORLZN"
                           "/UWUAP9pN1kD5A0TuW8lFwvAOXMge4EGKV7r9kNKjL2RAQi2swD9ERvTQ4gdwGKj"
                           "WRRXUOlXKL8fdGQA2G7eyt5SzQkEWQs="])

(deftest split-clearsign-test
  (testing "Can split clearsigned message correctly"
    (let [lines (str/split-lines sample-clearsign)
          result (split-clearsign lines)]
      (is (= sample-message-lines (:message-lines result)))
      (is (= sample-signature-lines (:signature-lines result)))))

  (testing "Handles empty message"
    (let [empty-msg (str/join "\n" 
                            ["-----BEGIN PGP SIGNED MESSAGE-----"
                             ""
                             "-----BEGIN PGP SIGNATURE-----"
                             "signature"
                             "-----END PGP SIGNATURE-----"])
          lines (str/split-lines empty-msg)
          result (split-clearsign lines)]
      (is (empty? (:message-lines result)))
      (is (= ["signature"] (:signature-lines result)))))

  (testing "Handles empty signature"
    (let [empty-sig (str/join "\n" 
                            ["-----BEGIN PGP SIGNED MESSAGE-----"
                             ""
                             "message"
                             "-----BEGIN PGP SIGNATURE-----"
                             "-----END PGP SIGNATURE-----"])
          lines (str/split-lines empty-sig)
          result (split-clearsign lines)]
      (is (= ["message"] (:message-lines result)))
      (is (empty? (:signature-lines result)))))

  (testing "Handles multiline message with empty lines"
    (let [multiline-msg (str/join "\n" 
                               ["-----BEGIN PGP SIGNED MESSAGE-----"
                                ""
                                "line 1"
                                ""
                                "line 2"
                                ""
                                "line 3"
                                "-----BEGIN PGP SIGNATURE-----"
                                "signature"
                                "-----END PGP SIGNATURE-----"])
          lines (str/split-lines multiline-msg)
          result (split-clearsign lines)]
      (is (= ["line 1" "" "line 2" "" "line 3"] (:message-lines result)))
      (is (= ["signature"] (:signature-lines result))))))

(deftest extract-message-test
  (testing "Can extract message from clearsigned text"
    (let [lines (str/split-lines sample-clearsign)
          result (extract-message lines)]
      (is (= (str/join "\n" sample-message-lines) result)))))

(deftest extract-signature-test
  (testing "Can extract signature from clearsigned text"
    (let [lines (str/split-lines sample-clearsign)
          result (extract-signature lines)]
      (is (= (str/join "\n" sample-signature-lines) result)))))

(deftest get-fossil-user-card-test
  (testing "Can extract Fossil user card"
    (let [fossil-lines ["A abc123" "B def456" "U test-user" "Z ghi789"]
          result (get-fossil-user-card fossil-lines)]
      (is (= "test-user" result)))))