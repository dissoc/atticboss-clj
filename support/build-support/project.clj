(defproject org.immutant/immutant-build-support "1.0.3-SNAPSHOT"
  :parent [org.immutant/immutant-support-parent _ :relative-path "../pom.xml"]
  :plugins [[lein-modules "0.1.0-SNAPSHOT"]]
  :dependencies [[org.clojure/data.zip "0.1.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [commons-io "2.0.1"]
                 [clj-glob "1.0.0"]
                 [org.clojars.tcrawley/codox.core "0.6.3"]
                 [digest "1.4.0"]
                 [cheshire _]
                 [leiningen-core _]])