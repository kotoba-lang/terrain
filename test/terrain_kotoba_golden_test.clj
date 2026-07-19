(ns terrain-kotoba-golden-test
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [kotoba.compiler.core :as compiler]
            [kotoba.compiler.ir :as ir]
            [terrain.noise :as noise]
            [terrain.water :as water]))

(deftest terrain-noise-height-and-water-goldens-agree-across-targets
  (let [source (slurp "src/terrain_golden.kotoba")
        names ['canonical-value-noise 'canonical-fbm 'canonical-height
               'primary-wave-amplitude 'primary-wave-wavelength 'primary-wave-speed
               'secondary-wave-direction-x 'secondary-wave-direction-y]
        fbm (noise/fbm-noise 1.5 2.5 4 2.0 0.5)
        waves (water/waves-from-wind [0.6 0.8] 10.0 1.2)
        primary (first waves) secondary (second waves)
        expected [(noise/value-noise 1.5 2.5)
                  fbm (* (Math/pow fbm 1.3) 120.0)
                  (:amplitude primary) (:wavelength primary) (:speed primary)
                  (first (:direction secondary)) (second (:direction secondary))]
        js-artifact (compiler/compile-source source :js-kotoba-v1)
        wasm-artifact (compiler/compile-source source :wasm32-browser-kotoba-v1)
        reference (mapv #(ir/execute (:kir js-artifact) % []) names)
        js64 (.encodeToString (java.util.Base64/getEncoder)
                              (.getBytes ^String (:source js-artifact) "UTF-8"))
        wasm64 (.encodeToString (java.util.Base64/getEncoder) (:bytes wasm-artifact))
        expected-js (str "[" (str/join "," (map #(Double/toString (double %)) expected)) "]")
        names-js (str "[" (str/join "," (map #(str "\"" % "\"") names)) "]")
        node-source
        (str "const expected=" expected-js ",names=" names-js ";"
             "const close=(a,b)=>Math.abs(a-b)<=1e-12;"
             "Promise.all([import('data:text/javascript;base64," js64 "'),"
             "WebAssembly.instantiate(Buffer.from('" wasm64 "','base64'),{})]).then(([j,w])=>{"
             "const a=j.instantiateKotoba({}),b=w.instance.exports;"
             "const js=names.map(n=>a[n]()),wa=names.map(n=>b[n]());"
             "if(!js.every((v,i)=>close(v,expected[i])&&Object.is(v,wa[i])))process.exit(2);"
             "}).catch(e=>{console.error(e);process.exit(99)})")
        node-result (shell/sh "node" "--input-type=module" "-e" node-source)]
    (is (every? true? (map #(< (Math/abs (- %1 %2)) 1.0e-12) reference expected)))
    (is (<= 0.0 (first reference) 1.0))
    (is (<= 0.0 (second reference) 1.0))
    (is (zero? (:exit node-result)) (:err node-result))
    (is (= :kotoba.floating-point/ieee-754-f32-f64-v7
           (:floating-point-policy js-artifact)))
    (is (= #{} (set (:effects (:kir js-artifact)))))))
