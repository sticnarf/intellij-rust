/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiElement
import com.intellij.util.text.SemVer
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.toolchain.RustChannel
import org.rust.ide.annotator.RsAnnotationHolder
import org.rust.ide.annotator.fixes.AddFeatureAttributeFix
import org.rust.lang.core.FeatureAvailability.*
import org.rust.lang.core.FeatureState.ACCEPTED
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.stubs.index.RsFeatureIndex
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder
import org.rust.lang.utils.evaluation.CfgEvaluator

class CompilerFeature(
    val name: String,
    val state: FeatureState,
    val since: SemVer?,
    cache: Boolean = true
) {

    constructor(
        name: String,
        state: FeatureState,
        since: String,
        cache: Boolean = true
    ) : this(name, state, SemVer.parseFromText(since)!!, cache)

    init {
        if (cache) {
            knownFeatures[name] = this
        }
    }

    fun availability(element: PsiElement): FeatureAvailability {
        val rsElement = element.ancestorOrSelf<RsElement>() ?: return UNKNOWN
        val version = rsElement.cargoProject?.rustcInfo?.version ?: return UNKNOWN

        if (state == ACCEPTED && (since == null || version.semver.isGreaterOrEqualThan(since.major, since.minor, since.patch))) {
            return AVAILABLE
        }

        val crate = rsElement.containingCrate ?: return UNKNOWN
        val origin = crate.origin
        val isStdlibPart = origin == PackageOrigin.STDLIB || origin == PackageOrigin.STDLIB_DEPENDENCY
        if (version.channel != RustChannel.NIGHTLY && !isStdlibPart) return NOT_AVAILABLE

        val cfgEvaluator = CfgEvaluator.forCrate(crate)
        val attrs = RsFeatureIndex.getFeatureAttributes(element.project, name)
        val possibleFeatureAttrs = attrs.asSequence()
            .filter { it.containingCrate == crate }
            .flatMap { cfgEvaluator.expandCfgAttrs(sequenceOf(it.metaItem)) }

        for (featureAttr in possibleFeatureAttrs) {
            if (featureAttr.name != "feature") continue
            val metaItems = featureAttr.metaItemArgs?.metaItemList.orEmpty()
            if (metaItems.any { feature -> feature.name == name }) return AVAILABLE
        }
        return CAN_BE_ADDED
    }

    fun check(
        holder: RsAnnotationHolder,
        element: PsiElement,
        presentableFeatureName: String,
        vararg fixes: LocalQuickFix
    ) = check(holder, element, null, "$presentableFeatureName is experimental", *fixes)

    fun check(
        holder: AnnotationHolder,
        element: PsiElement,
        presentableFeatureName: String,
        vararg fixes: LocalQuickFix
    ) = check(holder, element, null, "$presentableFeatureName is experimental", *fixes)

    fun check(
        holder: AnnotationHolder,
        startElement: PsiElement,
        endElement: PsiElement?,
        message: String,
        vararg fixes: LocalQuickFix
    ) {
        getDiagnostic(startElement, endElement, message, *fixes)?.addToHolder(holder)
    }

    fun check(
        holder: RsAnnotationHolder,
        startElement: PsiElement,
        endElement: PsiElement?,
        @InspectionMessage message: String,
        vararg fixes: LocalQuickFix
    ) {
        getDiagnostic(startElement, endElement, message, *fixes)?.addToHolder(holder)
    }

    fun addFeatureFix(element: PsiElement) =
        AddFeatureAttributeFix(name, element)

    private fun getDiagnostic(
        startElement: PsiElement,
        endElement: PsiElement?,
        message: String,
        vararg fixes: LocalQuickFix
    ) = when (availability(startElement)) {
        NOT_AVAILABLE -> RsDiagnostic.ExperimentalFeature(startElement, endElement, message, fixes.toList())
        CAN_BE_ADDED -> {
            val fix = addFeatureFix(startElement)
            RsDiagnostic.ExperimentalFeature(startElement, endElement, message, listOf(*fixes, fix))
        }
        else -> null
    }

    companion object {
        private val knownFeatures: MutableMap<String, CompilerFeature> = hashMapOf()

        fun find(featureName: String): CompilerFeature? = knownFeatures[featureName]
    }
}

enum class FeatureState {
    /**
     * Represents active features that are currently being implemented or
     * currently being considered for addition/removal.
     * Such features can be used only with nightly compiler with the corresponding feature attribute
     */
    ACTIVE,
    /**
     * Represents incomplete features that may not be safe to use and/or cause compiler crashes.
     * Such features can be used only with nightly compiler with the corresponding feature attribute
     */
    INCOMPLETE,
    /**
     * Those language feature has since been Accepted (it was once Active)
     * so such language features can be used with stable/beta compiler since some version
     * without any additional attributes
     */
    ACCEPTED
}

enum class FeatureAvailability {
    AVAILABLE,
    CAN_BE_ADDED,
    NOT_AVAILABLE,
    UNKNOWN
}
