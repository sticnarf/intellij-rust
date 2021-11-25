/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.selfType
import org.rust.lang.core.types.ty.TyUnknown

class RsSelfConventionInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsVisitor() {
            override fun visitFunction(m: RsFunction) {
                val traitOrImpl = when (val owner = m.owner) {
                    is RsAbstractableOwner.Trait -> owner.trait
                    is RsAbstractableOwner.Impl -> owner.impl.takeIf { owner.isInherent }
                    else -> null
                } ?: return

                val convention = SELF_CONVENTIONS.find {
                    m.identifier.text.startsWith(it.prefix) &&
                    m.identifier.text.endsWith(it.postfix ?: "")
                } ?: return
                if (m.selfSignature in convention.selfSignatures) return

                if (m.selfSignature == SelfSignature.BY_VAL) {
                    val selfType = traitOrImpl.selfType
                    val implLookup = ImplLookup.relativeTo(traitOrImpl)
                    if (selfType is TyUnknown || implLookup.isCopy(selfType)) return
                }

                holder.registerProblem(m.selfParameter ?: m.identifier, convention)
            }
        }

    private companion object {
        val SELF_CONVENTIONS = listOf(
            SelfConvention("as_", listOf(SelfSignature.BY_REF, SelfSignature.BY_MUT_REF)),
            SelfConvention("from_", listOf(SelfSignature.NO_SELF)),
            SelfConvention("into_", listOf(SelfSignature.BY_VAL)),
            SelfConvention("is_", listOf(SelfSignature.BY_REF, SelfSignature.NO_SELF)),
            SelfConvention("to_", listOf(SelfSignature.BY_MUT_REF), postfix = "_mut"),
            SelfConvention("to_", listOf(SelfSignature.BY_REF, SelfSignature.BY_VAL)),
        )
    }
}

enum class SelfSignature(val description: String) {
    NO_SELF("no self"),
    BY_VAL("self by value"),
    BY_REF("self by reference"),
    BY_MUT_REF("self by mutable reference");
}

private val RsFunction.selfSignature: SelfSignature
    get() {
        val self = selfParameter
        return when {
            self == null -> SelfSignature.NO_SELF
            self.isRef && self.mutability.isMut -> SelfSignature.BY_MUT_REF
            self.isRef -> SelfSignature.BY_REF
            else -> SelfSignature.BY_VAL
        }
    }

data class SelfConvention(
    val prefix: String,
    val selfSignatures: Collection<SelfSignature>,
    val postfix: String? = null
)

private fun RsProblemsHolder.registerProblem(element: PsiElement, convention: SelfConvention) {
    val selfTypes = convention.selfSignatures.joinToString(" or ") { it.description }

    val description = "methods called `${convention.prefix}*${convention.postfix ?: ""}` usually take $selfTypes; " +
        "consider choosing a less ambiguous name"

    registerProblem(element, description)
}
