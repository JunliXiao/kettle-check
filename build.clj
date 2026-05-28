(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'kettle-check/kettle-check)
(def version "1.0.0")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean [_]
      (b/delete {:path "target"}))

(defn uber [_]
      (clean nil)
      (b/copy-dir {:src-dirs ["src"]
                   :target-dir class-dir})
      (b/compile-clj {:basis basis
                      :src-dirs ["src"]
                      :class-dir class-dir})
      (b/uber {:class-dir class-dir
               :uber-file uber-file
               :basis basis
               :main 'kettle-check.core})
      (println "Build complete:" uber-file))
