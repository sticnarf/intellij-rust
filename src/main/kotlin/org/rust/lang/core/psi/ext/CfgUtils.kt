/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.VisibleForTesting
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.ProcMacroAttribute
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.utils.evaluation.CfgEvaluator
import org.rust.lang.utils.evaluation.ThreeValuedLogic
import org.rust.stdext.withPrevious

val PsiElement.isEnabledByCfg: Boolean get() = isEnabledByCfgInner(null)

/**
 * Returns `true` if it [isEnabledByCfg] and not inside an element under attribute procedural macro.
 *
 * A one exception is that it returns `true` for attribute macro itself:
 *
 * ```
 * #[a_macro]  // `true` for the attribute
 * fn foo() {} // `false` for the function
 * ```
 */
val PsiElement.existsAfterExpansion: Boolean
    get() {
        val status = getCodeStatus(null)
        return status == CfgDisabledOrAttrProcMacro.CODE || status == CfgDisabledOrAttrProcMacro.CFG_UNKNOWN
    }

fun PsiElement.isEnabledByCfg(crate: Crate): Boolean = isEnabledByCfgInner(crate)

private fun PsiElement.isEnabledByCfgInner(crate: Crate?): Boolean {
    val status = getCodeStatus(crate)
    return status == CfgDisabledOrAttrProcMacro.CODE || status == CfgDisabledOrAttrProcMacro.PROC_MACRO
        || status == CfgDisabledOrAttrProcMacro.CFG_UNKNOWN
}

val PsiElement.isCfgUnknown: Boolean
    get() = ancestors.filterIsInstance<RsDocAndAttributeOwner>().any { it.isCfgUnknownSelf }

@VisibleForTesting
fun PsiElement.getCodeStatus(crate: Crate?): CfgDisabledOrAttrProcMacro {
    for ((it, cameFrom) in ancestors.withPrevious().toList().asReversed()) {
        when (it) {
            is RsDocAndAttributeOwner -> {
                when (it.evaluateCfg(crate)) {
                    ThreeValuedLogic.False -> return CfgDisabledOrAttrProcMacro.CFG_DISABLED
                    ThreeValuedLogic.Unknown -> return CfgDisabledOrAttrProcMacro.CFG_UNKNOWN
                    ThreeValuedLogic.True -> Unit
                }
                if (it is RsAttrProcMacroOwner) {
                    val attr = ProcMacroAttribute.getProcMacroAttribute(it, explicitCrate = crate)
                    if (attr is ProcMacroAttribute.Attr && (cameFrom == null || !cameFrom.isAncestorOf(attr.attr))) {
                        return CfgDisabledOrAttrProcMacro.PROC_MACRO
                    }
                }
            }
            is RsMetaItem -> {
                if (
                    it.isRootMetaItem()
                    && it.name != "cfg_attr"
                    && it !in it.owner?.getQueryAttributes(explicitCrate = crate)?.metaItems.orEmpty()
                ) {
                    return CfgDisabledOrAttrProcMacro.CFG_DISABLED
                }
            }
        }
    }

    return CfgDisabledOrAttrProcMacro.CODE
}

enum class CfgDisabledOrAttrProcMacro {
    CFG_DISABLED,
    CFG_UNKNOWN,
    PROC_MACRO,
    CODE
}

/** Returns `true` if this attribute is `#[cfg_attr()]` and it is disabled */
val RsAttr.isDisabledCfgAttrAttribute: Boolean
    get() {
        val metaItem = metaItem
        if (metaItem.name != "cfg_attr") return false
        val condition = metaItem.metaItemArgs?.metaItemList?.firstOrNull() ?: return false
        val crate = containingCrate ?: return false
        return CfgEvaluator.forCrate(crate).evaluateCondition(condition) == ThreeValuedLogic.False
    }
