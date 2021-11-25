/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.getLocalVariableVisibleBindings
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.declaration
import org.rust.lang.core.types.type
import org.rust.openapiext.createSmartPointer

class InitializeWithDefaultValueFix(element: RsElement) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getText() = "Initialize with a default value"
    override fun getFamilyName() = name

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val variable = startElement.ancestorOrSelf<RsExpr>() ?: return
        val patBinding = variable.declaration as? RsPatBinding ?: return
        val declaration = patBinding.ancestorOrSelf<RsLetDecl>() ?: return
        val semicolon = declaration.semicolon ?: return
        val psiFactory = RsPsiFactory(project)
        val initExpr = RsDefaultValueBuilder(declaration.knownItems, declaration.containingMod, psiFactory, true)
            .buildFor(patBinding.type, (startElement as RsElement).getLocalVariableVisibleBindings())

        if (declaration.eq == null) {
            declaration.addBefore(psiFactory.createEq(), semicolon)
        }
        val addedInitExpr = declaration.addBefore(initExpr, semicolon)
        editor?.buildAndRunTemplate(declaration, listOf(addedInitExpr.createSmartPointer()))
    }

    companion object {
        fun createIfCompatible(element: RsElement): InitializeWithDefaultValueFix? {
            val variable = element.ancestorOrSelf<RsExpr>() ?: return null
            val patBinding = variable.declaration as? RsPatBinding ?: return null
            val declaration = patBinding.ancestorOrSelf<RsLetDecl>()
            if (declaration?.pat !is RsPatIdent || declaration.semicolon == null) return null
            return InitializeWithDefaultValueFix(element)
        }
    }
}
