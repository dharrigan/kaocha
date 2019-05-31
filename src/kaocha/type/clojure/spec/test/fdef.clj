(ns kaocha.type.clojure.spec.test.fdef
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [clojure.string :as str]
            [clojure.test :as test]
            [expound.alpha :as expound]
            [kaocha.report :as report]
            [kaocha.result :as result]
            [kaocha.testable :as testable]
            [kaocha.type :as type]
            [kaocha.specs]))

(alias 'stc 'clojure.spec.test.check)

(defn load-testable [{::stc/keys [opts] :as test-plan} sym]
  (let [nsname    (namespace sym)
        var       (resolve sym)]
    {:kaocha.testable/type  :kaocha.type/clojure.spec.test.fdef
     :kaocha.testable/id    (keyword sym)
     :kaocha.testable/meta  (meta var)
     :kaocha.testable/desc  (str sym)
     :kaocha.spec.fdef/sym  sym
     :kaocha.spec.fdef/var  var
     ::stc/opts             opts}))

(defn load-testables [test-plan syms]
  (->> syms
       (sort-by name)
       (map #(load-testable test-plan %))))

(defn report-success [check-results]
  (test/do-report
   {:type    :pass
    :message (str "Generative tests pass for "
                  (str/join ", " (map :sym check-results)))}))

(defn report-failure [check-results]
  (doseq [failed-check (filter :failure check-results)]
    (let [r       (stest/abbrev-result failed-check)
          failure (:failure r)]
      (test/do-report
       {:type     :fail
        :message  (expound/explain-results-str check-results)
        :expected (->> r :spec rest (apply hash-map) :ret)
        :actual   (if (instance? Throwable failure)
                    failure
                    (::stest/val failure))}))))

(defmethod testable/-run :kaocha.type/clojure.spec.test.fdef
  [{the-var  :kaocha.spec.fdef/var
    sym      :kaocha.spec.fdef/sym
    opts     ::stc/opts
    :as      testable} test-plan]
  (type/with-report-counters
    (test/do-report {:type :begin-test-var, :var the-var})
    (try (let [check-results  (stest/check sym {::stc/opts opts})
               checks-passed? (->> check-results (map :failure) (every? nil?))]
           (if checks-passed?
             (report-success check-results)
             (report-failure check-results)))
         (catch clojure.lang.ExceptionInfo e
           (when-not (:kaocha/fail-fast (ex-data e))
             (report/report-exception e)))
         (catch Throwable e
           (report/report-exception e)))
    (test/do-report {:type :end-test-var, :var the-var})
    (merge testable {:kaocha.result/count 1} (type/report-count))))

(s/def :kaocha.spec.fdef/var var?)
(s/def :kaocha.spec.fdef/sym qualified-symbol?)

(s/def :kaocha.type/clojure.spec.test.fdef
  (s/keys :req [:kaocha.testable/type
                :kaocha.testable/id
                :kaocha.spec.fdef/var]))