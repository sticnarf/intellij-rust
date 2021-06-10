/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.rust.lang.core.crate.Crate
import org.rust.lang.core.macros.proc.ProcMacroApplicationService
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve2.resolveToProcMacroWithoutPsiUnchecked
import org.rust.lang.core.stubs.RsAttributeOwnerStub
import org.rust.lang.core.stubs.common.RsAttrProcMacroOwnerPsiOrStub
import org.rust.lang.core.stubs.common.RsMetaItemPsiOrStub

/**
 * The class helps to check whether some [item][RsAttrProcMacroOwner] is a procedural macro call or not.
 * Use [RsAttrProcMacroOwner.procMacroAttribute] to get its value for some item.
 *
 * # Examples
 *
 * This function ([RsFunction]) is an attribute procedural macro call (i.e. it is [ProcMacroAttribute.Attr]).
 *
 * ```rust
 * #[foobar]
 * fn main() {}
 * ```
 *
 * This function ([RsFunction]) is not an attribute procedural macro (i.e. it is [ProcMacroAttribute.None])
 * because `no_mangle` is a built-in attribute (see [RS_BUILTIN_ATTRIBUTES]).
 *
 * ```rust
 * #[no_mangle]
 * fn foo() {}
 * ```
 *
 * This function ([RsFunction]) is not an attribute procedural macro (i.e. it is [ProcMacroAttribute.None])
 * because `rustfmt` is a built-in tool (see [RS_BUILTIN_TOOL_ATTRIBUTES]).
 *
 * ```rust
 * #[rustfmt::skip]
 * fn main() {}
 * ```
 *
 * This struct ([RsStructItem]) is not an attribute procedural macro, but it has a custom derive macro call
 * (i.e. it is [ProcMacroAttribute.Derive]) because a procedural macro attribute cannot be after a derive attribute.
 *
 * ```rust
 * #[derive(Foobar)]
 * #[baz]
 * struct S;
 * ```
 *
 * We consider this function ([RsFunction]) as not a macro ([ProcMacroAttribute.None]) because `tokio::main`
 * macro is hardcoded to be treated as a built-in attribute (see [RS_HARDCODED_PROC_MACRO_PROPERTIES])
 *
 * ```rust
 * #[tokio::main]
 * fn main() {}
 * ```
 *
 * This struct ([RsStructItem]) is considered as attribute procedural macro call
 * (i.e. it is [ProcMacroAttribute.Attr]), but `rustc` treats this struct as just a struct with a custom
 * derive `serde::Deserialize` and a helper attribute `serde(deny_unknown_fields)`.
 * We intentionally don't support such `rustc` behavior because it is too hard to implement,
 * and it is going to be deprecated (see https://github.com/rust-lang/rust/issues/79202)
 *
 * ```rust
 * #[serde(deny_unknown_fields)]
 * #[derive(serde::Deserialize)]
 * struct S;
 * ```
 *
 * See `ProcMacroAttributeTest` for more examples
 */
sealed class ProcMacroAttribute<out T : RsMetaItemPsiOrStub> {
    abstract val attr: T?

    /**
     * The item has no attribute procedural macros or custom derive attributes,
     * but may have built-in derive attributes
     */
    object None : ProcMacroAttribute<Nothing>() {
        override val attr: Nothing? get() = null
        override fun toString(): String = "None"
    }

    /** The item has no attribute procedural macros, but may have custom derive attributes */
    object Derive : ProcMacroAttribute<Nothing>() {
        override val attr: Nothing? get() = null
        override fun toString(): String = "Derive"
    }

    /**
     * The item **has** attribute procedural macro call, it is [attr]
     */
    data class Attr<T : RsMetaItemPsiOrStub>(
        override val attr: T,
        /**
         * The index of [attr] (starting from 0) in [RsDocAndAttributeOwner.queryAttributes]
         * of the attribute owner
         */
        val index: Int
    ) : ProcMacroAttribute<T>()

    companion object {
        /**
         *
         * Can't be after derive:
         *
         * ```
         * #[derive(Foo)]
         * #[foo] // NOT an attribute macro
         * struct S
         * ```
         *
         * Legacy derive helpers aren't supported
         * https://github.com/rust-lang/rust/issues/79202
         */
        fun <T : RsMetaItemPsiOrStub> getProcMacroAttributeRaw(
            owner: RsAttrProcMacroOwnerPsiOrStub<T>,
            stub: RsAttributeOwnerStub? = if (owner is RsDocAndAttributeOwner) owner.attributeStub else owner as RsAttributeOwnerStub,
            explicitCrate: Crate? = null,
            explicitCustomAttributes: CustomAttributes? = null,
        ): ProcMacroAttribute<T> {
            if (!ProcMacroApplicationService.isEnabled()) return None
            if (stub != null) {
                if (!stub.mayHaveCustomAttrs) {
                    return if (stub.mayHaveCustomDerive) Derive else None
                }
            }

            val crate = explicitCrate ?: owner.containingCrate ?: return None
            val customAttributes = explicitCustomAttributes ?: CustomAttributes.fromCrate(crate)

            owner.getQueryAttributes(crate, stub, fromOuterAttrsOnly = true).metaItems.forEachIndexed { index, meta ->
                if (meta.name == "derive") return Derive
                if (RsProcMacroPsiUtil.canBeProcMacroAttributeCallWithoutContextCheck(meta, customAttributes)) {
                    return Attr(meta, index)
                }
            }
            return None
        }

        fun getProcMacroAttribute(
            owner: RsAttrProcMacroOwner,
            stub: RsAttributeOwnerStub? = owner.attributeStub,
            explicitCrate: Crate? = null,
        ): ProcMacroAttribute<RsMetaItem> {
            return when (val attr = getProcMacroAttributeRaw(owner, stub, explicitCrate)) {
                Derive -> Derive
                None -> None
                is Attr -> {
                    val props = attr.attr.resolveToProcMacroWithoutPsiUnchecked(checkIsMacroAttr = false)?.props
                    if (props != null && props.treatAsBuiltinAttr && RsProcMacroPsiUtil.canFallbackItem(owner)) {
                        None
                    } else {
                        attr
                    }
                }
            }
        }
    }
}
