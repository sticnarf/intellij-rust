/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.rust.openapiext.isUnitTestMode


private val RS_HARDCODED_PROC_MACRO_PROPERTIES: Map<String, Map<String, KnownProcMacroProperties>> = mapOf(
    "tokio-macros" to mapOf(
        "main" to KnownProcMacroProperties(KnownProcMacroKind.ASYNC_MAIN),
        "test" to KnownProcMacroProperties(KnownProcMacroKind.ASYNC_TEST),
    ),
    "async-attributes" to mapOf(
        "main" to KnownProcMacroProperties(KnownProcMacroKind.ASYNC_MAIN),
        "test" to KnownProcMacroProperties(KnownProcMacroKind.ASYNC_TEST),
        "bench" to KnownProcMacroProperties(KnownProcMacroKind.ASYNC_BENCH),
    ),
    "tracing-attributes" to mapOf("instrument" to KnownProcMacroProperties(KnownProcMacroKind.IDENTITY)),
    "proc-macro-error-attr" to mapOf("proc_macro_error" to KnownProcMacroProperties(KnownProcMacroKind.IDENTITY)),
    "actix-web-codegen" to mapOf("main" to KnownProcMacroProperties(KnownProcMacroKind.ASYNC_MAIN)),
    "actix_derive" to mapOf(
        "main" to KnownProcMacroProperties(KnownProcMacroKind.ASYNC_MAIN),
        "test" to KnownProcMacroProperties(KnownProcMacroKind.ASYNC_TEST),
    ),
    "serial_test_derive" to mapOf("serial" to KnownProcMacroProperties(KnownProcMacroKind.TEST_WRAPPER)),
    "cortex-m-rt-macros" to mapOf("entry" to KnownProcMacroProperties(KnownProcMacroKind.CUSTOM_MAIN)),
    "test-case" to mapOf("test_case" to KnownProcMacroProperties(KnownProcMacroKind.CUSTOM_TEST)),
    "ndk-macro" to mapOf("main" to KnownProcMacroProperties(KnownProcMacroKind.CUSTOM_MAIN)),
    "quickcheck_macros" to mapOf("quickcheck" to KnownProcMacroProperties(KnownProcMacroKind.CUSTOM_TEST)),
    "async-recursion" to mapOf("async_recursion" to KnownProcMacroProperties(KnownProcMacroKind.IDENTITY)),
    "paw-attributes" to mapOf("main" to KnownProcMacroProperties(KnownProcMacroKind.CUSTOM_MAIN)),
    "interpolate_name" to mapOf("interpolate_test" to KnownProcMacroProperties(KnownProcMacroKind.CUSTOM_TEST_RENAME)),
    "ntest_test_cases" to mapOf("test_case" to KnownProcMacroProperties(KnownProcMacroKind.CUSTOM_TEST_RENAME)),
    "spandoc-attribute" to mapOf("spandoc" to KnownProcMacroProperties(KnownProcMacroKind.IDENTITY)),
    "log-derive" to mapOf(
        "logfn" to KnownProcMacroProperties(KnownProcMacroKind.IDENTITY),
        "logfn_inputs" to KnownProcMacroProperties(KnownProcMacroKind.IDENTITY),
    ),
    "wasm-bindgen-test-macro" to mapOf("wasm_bindgen_test" to KnownProcMacroProperties(KnownProcMacroKind.CUSTOM_TEST)),
    "test-env-log" to mapOf("test" to KnownProcMacroProperties(KnownProcMacroKind.CUSTOM_TEST)),
    "parameterized-macro" to mapOf("parameterized" to KnownProcMacroProperties(KnownProcMacroKind.CUSTOM_TEST_RENAME)),
    "alloc_counter_macro" to mapOf(
        "no_alloc" to KnownProcMacroProperties(KnownProcMacroKind.IDENTITY),
        "count_alloc" to KnownProcMacroProperties(KnownProcMacroKind.IDENTITY),
    ),
    "uefi-macros" to mapOf("entry" to KnownProcMacroProperties(KnownProcMacroKind.CUSTOM_MAIN)),
)

fun getHardcodeProcMacroProperties(packageName: String, macroName: String): KnownProcMacroProperties {
    val props = RS_HARDCODED_PROC_MACRO_PROPERTIES[packageName]?.get(macroName)
    if (props != null) {
        return props
    }

    if (isUnitTestMode && packageName == "test_proc_macros" && macroName == "attr_hardcoded_not_a_macro") {
        return KnownProcMacroProperties(KnownProcMacroKind.IDENTITY)
    }

    return KnownProcMacroProperties.DEFAULT
}

data class KnownProcMacroProperties(
    val kind: KnownProcMacroKind
) {
    val treatAsBuiltinAttr: Boolean
        get() = kind.treatAsBuiltinAttr

    companion object {
        val DEFAULT: KnownProcMacroProperties = KnownProcMacroProperties(
            KnownProcMacroKind.DEFAULT_PURE
        )
    }
}

enum class KnownProcMacroKind {
    DEFAULT_PURE,
    IDENTITY,
    ASYNC_MAIN,
    ASYNC_TEST,
    ASYNC_BENCH,
    TEST_WRAPPER,
    CUSTOM_MAIN,
    CUSTOM_TEST,
    CUSTOM_TEST_RENAME,
    ;

    val treatAsBuiltinAttr: Boolean
        get() = this != DEFAULT_PURE
}
