;; Copyright (c) Rich Hickey. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns cljs.test-util
  (:require [clojure.java.io :as io]
            [clojure.string :as string])
  (:import [java.lang ProcessBuilder]
           [java.io
            BufferedReader File IOException InputStreamReader StringWriter Writer]))

(defn delete-out-files
  "Processed files are only copied/written if input has changed. In test case it
   makes sense to write files always, in case the processing logic has changed."
  ([]
   (delete-out-files "out"))
  ([directory]
   (doseq [f (file-seq (io/file directory))
           :when (.isFile f)]
     (.delete f))))

(defn delete-node-modules []
  (let [nm (io/file "node_modules")]
    (while (.exists nm)
      (doseq [f (file-seq nm)]
        (.delete f)))))

(defn document-write?
  "Returns true if the string `s` contains a document.write statement to
  load the namespace `ns`, otherwise false."
  [s ns]
  (->> (format "document.write('<script>goog.require(\"%s\");</script>');" ns)
       (string/index-of s)
       (some?)))

(defn project-with-modules
  "Returns the build config for a project that uses Google Closure modules."
  [output-dir]
  {:inputs (str (io/file "src" "test" "cljs"))
   :opts
   {:main "module-test.main"
    :output-dir output-dir
    :optimizations :advanced
    :verbose true
    :modules
    {:cljs-base
     {:output-to (str (io/file output-dir "module-main.js"))}
     :module-a
     {:output-to (str (io/file output-dir "module-a.js"))
      :entries #{'module-test.modules.a}}
     :module-b
     {:output-to (str (io/file output-dir "module-b.js"))
      :entries #{'module-test.modules.b}}}
    :closure-warnings {:check-types :off}}})

(defn tmp-dir
  "Returns the temporary directory of the system."
  []
  (System/getProperty "java.io.tmpdir"))

(defn platform-path [path]
  (.replace path \/ (.charAt (str File/separator) 0)))

(defn- alive? [proc]
  (try (.exitValue proc) false (catch IllegalThreadStateException _ true)))

(defn- pipe [^Process proc in ^Writer out]
  ;; we really do want system-default encoding here
  (with-open [^java.io.Reader in (-> in InputStreamReader. BufferedReader.)]
    (loop [buf (char-array 1024)]
      (when (alive? proc)
        (try
          (let [len (.read in buf)]
            (when-not (neg? len)
              (.write out buf 0 len)
              (.flush out)))
          (catch IOException e
            (when (and (alive? proc) (not (.contains (.getMessage e) "Stream closed")))
              (.printStackTrace e *err*))))
        (recur buf)))))

(defn run-command!
  "Runs a shell command using ProcessBuilder and returns a tuple
   [output err-output exit-code], where output and err-output are
   the standard and error output of the command (as strings) and
   exit-code is the exit code of the command.

   Takes args and opts, which may contain the following options:

     :pwd - the working directory as a java.io.File"
  [args {:keys [pwd] :as opts}]
  (let [builder (cond-> (ProcessBuilder. args)
                  (:pwd opts) (.directory (:pwd opts)))
        proc    (.start builder)
        is      (.getInputStream proc)
        iw      (StringWriter. (* 16 1024 1024))
        es      (.getErrorStream proc)
        ew      (StringWriter. (* 1024 1024))
        _       (do (.start
                     (Thread.
                      (bound-fn [] (pipe proc is iw))))
                    (.start
                     (Thread.
                      (bound-fn [] (pipe proc es ew)))))
        code    (.waitFor proc)]
    [(str iw) (str ew) code]))
