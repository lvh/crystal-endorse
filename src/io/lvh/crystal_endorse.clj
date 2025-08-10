(ns io.lvh.crystal-endorse
  (:gen-class)
  (:require
   [clojure.string :as str]
   [meander.epsilon :as m]))

(def five-dashes (->> \- (repeat 5) str/join))
(defn ^:private dashed [m] (str five-dashes m five-dashes))
(def begin-message (dashed "BEGIN PGP SIGNED MESSAGE"))
(def begin-signature (dashed "BEGIN PGP SIGNATURE"))
(def end-signature (dashed "END PGP SIGNATURE"))

;; I am sure there is some bananas bug because of line-seq, which uses
;; BufferedReader, which in turn splits lines on \n, \r, or \r\n. The OpenGPG
;; spec, which I have not read in detail on this particular topic, seems to be
;; predictably wishy-washy on the subject; suggesting \r\n in transit but
;; translated to "native". I have never seen any implementation produce anything
;; but \n, though--admittedly I probably only ever checked on *nix :-)

(defn sign
  []
  )

(defn split-clearsign
  [lines]
  (m/find
    lines
    [begin-message
     ""
     . (m/and !message-lines (m/not ~begin-signature)) ...
     ~begin-signature
     . (m/and !signature-lines (m/not ~end-signature)) ...
     ~end-signature]
    {:message-lines !message-lines
     :signature-lines !signature-lines}))

(defn get-fossil-user-card
  [fossil-artifact-lines]
  ;; I'm sure there is no common cryptographic principle suggesting inevitable
  ;; doom should one parse an untrusted message.
  (let [user-card? (fn [s] (str/starts-with? s "U "))
        [user-card & other-user-cards] (filter user-card? fossil-artifact-lines)]
    (assert (nil? other-user-cards) "expecting exactly 1 user card")
    (subs user-card 2)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  )
