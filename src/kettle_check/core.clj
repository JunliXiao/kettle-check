(ns kettle-check.core
  (:require [clojure.xml]
            [clojure.java.io]
            [clojure.string])
  (:gen-class))

;; Define variables for the program as requested
(def KETTLE-WORKFLOW-ROOT "E:/kettle-etl-workflow_MSSQL")
(def TARGET-KJB "E:\\kettle-etl-workflow_MSSQL\\control\\ETL_C_RERUN.kjb")

(defn filename-tag?
  "Checks if a given tag is a :filename tag (case-insensitive keyword or string check)."
  [tag]
  (and tag (= "filename" (clojure.string/lower-case (name tag)))))

(defn get-text-content
  "Extracts and joins all string children inside a node's content."
  [node]
  (if (map? node)
    (clojure.string/join "" (filter string? (:content node)))
    ""))

(defn extract-filenames
  "Recursively searches the XML node tree to find text content of <filename> elements."
  [node]
  (cond
    (map? node)
    (if (filename-tag? (:tag node))
      [(get-text-content node)]
      (mapcat extract-filenames (:content node)))

    (coll? node)
    (mapcat extract-filenames node)

    :else
    []))

(defn resolve-kettle-path
  "Replaces common Kettle directory variables with the absolute path of the current file's directory."
  [path current-file-dir]
  (let [parent-path (if current-file-dir
                      (-> (.getAbsolutePath (clojure.java.io/file current-file-dir))
                          (clojure.string/replace "\\" "/")
                          (java.util.regex.Matcher/quoteReplacement))
                      "")]
    (clojure.string/replace path
                            #"(?i)\$\{Internal\.(Entry|Job|Workflow|Transformation)\.(Current|Filename|Descriptor)\.Directory\}"
                            parent-path)))

(defn resolve-workflow-root
  "Replaces KETTLE_WORKFLOW_ROOT variable with the assigned argument."
  [path workflow-root]
  (clojure.string/replace path "${KETTLE_WORKFLOW_ROOT}" workflow-root))

(defn resolve-path
  "Resolves a raw file path (possibly containing Kettle variables or relative references)
  against the current file directory and the workflow root."
  [raw-path current-file-dir workflow-root]
  (let [path-with-vars (resolve-kettle-path raw-path current-file-dir)]
    (clojure.java.io/file (resolve-workflow-root path-with-vars workflow-root))))

(defn scan-file
  "Recursively scans a Kettle job (.kjb) or transformation (.ktr) file.
  Tracks visited files to avoid circular reference loops.
  Returns the updated set of visited absolute file paths."
  [file workflow-root visited depth]
  (let [abs-path (try (.getCanonicalPath file) (catch Exception _ (.getAbsolutePath file)))
        indent (apply str (repeat depth "  "))]
    (cond
      (contains? visited abs-path)
      (do
        ;;(println (str indent "- [Already Scanned] " (.getName file)))
        visited)

      (and (not (clojure.string/ends-with? (clojure.string/lower-case abs-path) ".kjb"))
          (not (clojure.string/ends-with? (clojure.string/lower-case abs-path) ".ktr")))
      (do
        ;;(println (str indent "- [Unsupported Type] " (.getName file) " (Path: " abs-path ")"))
        visited)

      (not (.exists file))
      (do
        (println (str indent "- [❗Missing File] " (.getName file) " (Path: " abs-path ")"))
        visited)

      :else
      (do
        (println (str indent "- " (.getName file) " (" abs-path ")"))
        (let [new-visited (conj visited abs-path)
              xml-root (try
                         (clojure.xml/parse file)
                         (catch Exception e
                           (println (str indent "  [Error parsing XML: " (.getMessage e) "]"))
                           nil))]
          (if xml-root
            (let [raw-filenames (extract-filenames xml-root)
                  current-dir (.getParentFile file)]
              (reduce
                (fn [v raw-fn]
                  (if (not (clojure.string/ends-with? (clojure.string/lower-case raw-fn) ".csv"))
                    ;;(println (str indent "  -> Found reference: " raw-fn))
                    (let [resolved-file (resolve-path raw-fn current-dir workflow-root)]
                      (scan-file resolved-file workflow-root v (inc depth)))
                    v))
                new-visited
                raw-filenames))
            new-visited))))))

(defn check-and-parse
  "Starts the recursive check from the initial TARGET-KJB file."
  [workflow-root target-kjb]
  (let [target-file (clojure.java.io/file target-kjb)
        resolved-file (if (.isAbsolute target-file)
                        target-file
                        (clojure.java.io/file workflow-root target-kjb))
        file-name (.getName resolved-file)
        is-entry-kjb? (clojure.string/ends-with? (clojure.string/lower-case file-name) ".kjb")]
    (println "=== Recursive Kettle Checker ===")
    (println "KETTLE_WORKFLOW_ROOT:" workflow-root)
    (println "TARGET_KJB:          " target-kjb)
    (println "Resolved Entry Path: " (.getAbsolutePath resolved-file))
    (println "--------------------------------")
    (cond
      (not is-entry-kjb?)
      (println "Error: The target entry file is not a .kjb file.")

      (not (.exists resolved-file))
      (println "Error: The target entry file does not exist.")

      :else
      (do
        (println "Scan results:")
        (scan-file resolved-file workflow-root #{} 0)
        (println "--------------------------------")))))

(defn -main
  "Main entry point for running via Clojure CLI."
  [& args]
  (let [root (or (first args) KETTLE-WORKFLOW-ROOT)
        kjb (or (second args) TARGET-KJB)]
    (check-and-parse root kjb)))
