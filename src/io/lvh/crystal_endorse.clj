(ns io.lvh.crystal-endorse
  (:gen-class)
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [babashka.fs :as fs]
   [babashka.process :as process]
   [clojure.tools.cli :refer [parse-opts]]
   [meander.epsilon :as m]))

;; Helper function to execute shell commands since babashka.process doesn't have a shell function in version 0.0.1
(defn shell [{:keys [out err] :as opts} & args]
  (let [pb (apply process/pb args)
        proc (process/process pb)
        exit-code (.waitFor ^Process proc)
        stdout (when (= :string out) (slurp (.getInputStream ^Process proc)))
        stderr (when (= :string err) (slurp (.getErrorStream ^Process proc)))]
    {:exit exit-code
     :out stdout
     :err stderr}))

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
  "Creates a clearsigned message using SSH signatures.
   
   Options:
     :key - path to the SSH private key (default: ~/.ssh/id_ed25519)
     :identity - user identity (default: derived from key)
     :allow-stdin - allow reading message from stdin (default: true)
     :message - the message to sign (alternative to stdin)
     :armor - whether to armor/clearsign the output (default: true)
  "
  [& {:keys [key identity allow-stdin message armor]
      :or {key (str (fs/home) "/.ssh/id_ed25519") 
           allow-stdin true
           armor true}}]
  (let [temp-msg-file (when (and message (not allow-stdin))
                       (let [temp-file (str (fs/create-file (fs/path (fs/temp-dir) (str "crystal-endorse-" (System/currentTimeMillis)))))]
                         (spit temp-file message)
                         (str temp-file)))
        msg-source (cond 
                    temp-msg-file ["--file" temp-msg-file]
                    (not allow-stdin) (throw (ex-info "No message source available" 
                                                     {:allow-stdin allow-stdin :message-provided (boolean message)}))
                    :else [])
        key-args (if key ["--key" key] [])
        identity-args (if identity ["--identity" identity] [])
        ssh-args (concat key-args identity-args ["--armor"] msg-source)
        result (try
                 (apply shell {:out :string :err :string} "ssh-keygen" "-Y" "sign" ssh-args)
                 (catch Exception e
                   (throw (ex-info "SSH signing failed" {:cause e}))))
        signature (:out result)]
    (when temp-msg-file
      (fs/delete temp-msg-file))
    (if armor
      (let [msg-lines (if message 
                        (str/split-lines message) 
                        (line-seq (io/reader *in*)))
            header [begin-message ""]]
        (str/join "\n" (concat header msg-lines [begin-signature] (str/split-lines signature) [end-signature])))
      signature)))

(defn split-clearsign
  "Split a clearsigned message into message and signature parts.
   Returns nil if the input doesn't match the expected clearsign format."
  [lines]
  (let [begin-msg-pos (.indexOf lines begin-message)
        begin-sig-pos (.indexOf lines begin-signature)
        end-sig-pos (.indexOf lines end-signature)]
    (if (or (= -1 begin-msg-pos) (= -1 begin-sig-pos) (= -1 end-sig-pos)
            (<= begin-sig-pos begin-msg-pos)
            (<= end-sig-pos begin-sig-pos))
      nil
      (let [message-start (+ begin-msg-pos 2) ;; Skip begin-message and the empty line after
            message-lines (subvec lines message-start begin-sig-pos)
            signature-start (inc begin-sig-pos)
            signature-lines (subvec lines signature-start end-sig-pos)]
        {:message-lines message-lines
         :signature-lines signature-lines}))))

;; This is kept for backwards compatibility with code that might expect the meander pattern matching
;; approach, but it's commented out for now since we're using the simpler approach above
#_(defn split-clearsign-m
  "Split a clearsigned message into message and signature parts using meander pattern matching."
  [lines]
  (try
    (m/find
      lines
      [begin-message
       ""
       . (m/and !message-lines (m/not ~begin-signature)) ...
       ~begin-signature
       . (m/and !signature-lines (m/not ~end-signature)) ...
       ~end-signature]
      {:message-lines !message-lines
       :signature-lines !signature-lines})
    (catch Exception _
      nil)))

(defn get-fossil-user-card
  [fossil-artifact-lines]
  ;; I'm sure there is no common cryptographic principle suggesting inevitable
  ;; doom should one parse an untrusted message.
  (let [user-card? (fn [s] (str/starts-with? s "U "))
        [user-card & other-user-cards] (filter user-card? fossil-artifact-lines)]
    (assert (some? user-card) "expecting at least 1 user card")
    (assert (nil? other-user-cards) "expecting exactly 1 user card")
    (subs user-card 2)))

(def cli-options
  [["--key PATH" "Path to SSH private key"
    :id :key
    :default (str (fs/home) "/.ssh/id_ed25519")
    :validate [fs/exists? "Key file must exist"]]
   
   ["--identity IDENTITY" "User identity"
    :id :identity]
   
   ["--verify" "Verify a signed message"
    :id :verify
    :default false]
   
   ["--extract-signature" "Extract signature from clearsigned message"
    :id :extract-signature
    :default false]
   
   ["--extract-message" "Extract message from clearsigned message"
    :id :extract-message
    :default false]
   
   ["--file PATH" "Read message from file instead of stdin"
    :id :file
    :validate [fs/exists? "File must exist"]]
   
   ["-h" "--help" "Show this help"
    :id :help
    :default false]])

(defn usage [options-summary]
  (str/join \newline
           ["crystal-endorse - OpenSSH-based signing shim for GPG clearsign"
            ""
            "Usage: crystal-endorse [options] [file]"
            ""
            "Options:"
            options-summary
            ""
            "Examples:"
            "  echo 'Hello, world!' | crystal-endorse"
            "  crystal-endorse --verify < signed-message.txt"
            "  crystal-endorse --file message.txt --key ~/.ssh/id_rsa"]))

(defn verify-signature 
  "Verify a clearsigned message"
  [input]
  (let [lines (if (string? input) (str/split-lines input) input)
        {:keys [message-lines signature-lines]} (split-clearsign lines)
        msg-file (str (fs/create-file (fs/path (fs/temp-dir) (str "crystal-endorse-msg-" (System/currentTimeMillis)))))
        sig-file (str (fs/create-file (fs/path (fs/temp-dir) (str "crystal-endorse-sig-" (System/currentTimeMillis)))))]
    (try
      (spit msg-file (str/join "\n" message-lines))
      (spit sig-file (str/join "\n" signature-lines))
      (let [result (try 
                     (shell {:out :string :err :string}
                            "ssh-keygen" "-Y" "verify" "-n" "file" "-f" "allowed_signers" "-I" "signer" "-s" (str sig-file) "-m" (str msg-file))
                     (catch Exception e
                       (println "Error verifying signature:" (.getMessage e))
                       {:exit 1}))]
        (= 0 (:exit result)))
      (finally
        (fs/delete msg-file)
        (fs/delete sig-file)))))

(defn extract-message
  "Extract message from clearsigned text"
  [input]
  (let [lines (if (string? input) (str/split-lines input) input)
        {:keys [message-lines]} (split-clearsign lines)]
    (when message-lines
      (str/join "\n" message-lines))))

(defn extract-signature
  "Extract signature from clearsigned text"
  [input]
  (let [lines (if (string? input) (str/split-lines input) input)
        {:keys [signature-lines]} (split-clearsign lines)]
    (when signature-lines
      (str/join "\n" signature-lines))))

(defn -main
  "CLI entry point for crystal-endorse"
  [& args]
  (let [parsed (parse-opts args cli-options)
        options (:options parsed)
        summary (:summary parsed)
        errors (:errors parsed)]
    (cond
      (:help options) (do (println (usage summary)) (System/exit 0))
      errors (do (doseq [error errors] (println error)) (System/exit 1))
      
      (:verify options)
      (let [input (line-seq (io/reader *in*))]
        (if (verify-signature input)
          (do (println "Good signature") (System/exit 0))
          (do (println "Bad signature") (System/exit 1))))
      
      (:extract-message options)
      (println (extract-message (line-seq (io/reader *in*))))
      
      (:extract-signature options)
      (println (extract-signature (line-seq (io/reader *in*))))
      
      :else
      (let [message (when-let [file (:file options)]
                      (slurp file))]
        (println (sign :key (:key options)
                       :identity (:identity options)
                       :message message
                       :allow-stdin (not (:file options)))))))
  (shutdown-agents))
