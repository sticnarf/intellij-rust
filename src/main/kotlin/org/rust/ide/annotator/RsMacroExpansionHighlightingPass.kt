/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.*
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.macros.*
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.RsAttrProcMacroOwner
import org.rust.lang.core.psi.ext.RsPossibleMacroCall
import org.rust.lang.core.psi.ext.existsAfterExpansion
import org.rust.lang.core.psi.ext.expansion
import org.rust.stdext.removeLast

class RsMacroExpansionHighlightingPassFactory(
    val project: Project,
    registrar: TextEditorHighlightingPassRegistrar
) : DirtyScopeTrackingHighlightingPassFactory {
    private val myPassId: Int = registrar.registerTextEditorHighlightingPass(
        this,
        null,
        null,
        false,
        -1
    )

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        if (project.macroExpansionManager.macroExpansionMode !is MacroExpansionMode.New) return null
        if (!MACRO_HIGHLIGHTING_ENABLED_KEY.asBoolean()) return null

        val restrictedRange = FileStatusMap.getDirtyTextRange(editor, passId) ?: return null
        return RsMacroExpansionHighlightingPass(file, restrictedRange, editor.document)
    }

    override fun getPassId(): Int = myPassId

    companion object {
        @JvmStatic
        private val MACRO_HIGHLIGHTING_ENABLED_KEY = Registry.get("org.rust.lang.highlight.macro.body")
    }
}

class RsMacroExpansionHighlightingPass(
    private val file: PsiFile,
    private val restrictedRange: TextRange,
    document: Document
) : TextEditorHighlightingPass(file.project, document) {
    private val results = mutableListOf<HighlightInfo>()

    private fun createAnnotators(): List<Annotator> = listOf(
        RsEdition2018KeywordsAnnotator(),
        RsHighlightingAnnotator(),
        RsHighlightingMutableAnnotator(),
        RsCfgDisabledCodeAnnotator(),
        RsFormatMacroAnnotator(),
    )

    @Suppress("UnstableApiUsage")
    override fun doCollectInformation(progress: ProgressIndicator) {
        val macros = mutableListOf<PreparedMacroCall>()

        PsiTreeUtil.processElements(file) {
            if (it is RsMacroCall) {
                if (it.macroArgument?.textRange?.intersects(restrictedRange) != true) return@processElements true
                macros += it.prepare() ?: return@processElements true
            } else if (it is RsAttrProcMacroOwner) {
                if (it.textRange?.intersects(restrictedRange) != true) return@processElements true
                macros += it.procMacroAttribute.attr?.prepare() ?: return@processElements true
            }
            true // Continue
        }

        if (macros.isEmpty()) return

        val annotators = createAnnotators()
        while (macros.isNotEmpty()) {
            val macro = macros.removeLast()
            val holder = AnnotationHolderImpl(AnnotationSession(macro.expansion.file))

            for (element in macro.elementsForHighlighting) {
                for (ann in annotators) {
                    ProgressManager.checkCanceled()
                    holder.runAnnotatorWithContext(element, ann)
                }

                if (element is RsMacroCall) {
                    macros += element.prepare() ?: continue
                } else if (element is RsAttrProcMacroOwner) {
                    macros += element.procMacroAttribute.attr?.prepare() ?: continue
                }
            }

            for (ann in holder) {
                mapAndCollectAnnotation(macro, ann)
            }
        }
    }

    private fun mapAndCollectAnnotation(macro: PreparedMacroCall, ann: Annotation) {
        val originRange = TextRange(ann.startOffset, ann.endOffset)
        val originInfo = HighlightInfo.fromAnnotation(ann)
        val mappedRanges = mapRangeFromExpansionToCallBody(macro.expansion, macro.macroCall, originRange)
        for (mappedRange in mappedRanges) {
            results += originInfo.copyWithRange(mappedRange)
        }
    }

    override fun doApplyInformationToEditor() {
        UpdateHighlightersUtil.setHighlightersToEditor(
            myProject,
            myDocument,
            restrictedRange.startOffset,
            restrictedRange.endOffset,
            results,
            colorsScheme,
            id
        )
    }
}

private fun RsPossibleMacroCall.prepare(): PreparedMacroCall? {
    if (this is RsMacroCall && macroArgument == null) return null // special macros should not be highlighted
    if (!existsAfterExpansion) return null
    val expansion = expansion ?: return null
    return PreparedMacroCall(this, expansion)
}

private data class PreparedMacroCall(val macroCall: RsPossibleMacroCall, val expansion: MacroExpansion) {
    val elementsForHighlighting: List<PsiElement>
        get() {
            if (expansion.ranges.isEmpty()) return emptyList()
            // Don't try to restrict range by `getElementsInRange`: it does not return all ancestors
            // even if `includeAllParents = true`
            return CollectHighlightsUtil.getElementsInRange(expansion.file, 0, expansion.file.textLength)
        }
}

private fun HighlightInfo.copyWithRange(newRange: TextRange): HighlightInfo {
    val forcedTextAttributesKey = forcedTextAttributesKey
    val forcedTextAttributes = forcedTextAttributes
    val description = description
    val toolTip = toolTip

    val b = HighlightInfo.newHighlightInfo(type)
        .range(newRange)
        .severity(severity)

    if (forcedTextAttributesKey != null) {
        b.textAttributes(forcedTextAttributesKey)
    } else if (forcedTextAttributes != null) {
        b.textAttributes(forcedTextAttributes)
    }

    if (description != null) {
        b.description(description)
    }
    if (toolTip != null) {
        b.escapedToolTip(toolTip)
    }
    if (isAfterEndOfLine) {
        b.endOfLine()
    }

    return b.createUnconditionally()
}
