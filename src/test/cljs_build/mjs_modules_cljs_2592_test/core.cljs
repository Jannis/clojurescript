(ns mjs-modules-cljs-2592-test.core
  (:require [iterall]
            [graphql]))

(enable-console-print!)

(println "Is collection:" (iterall/isCollection #js [1 2]))
