/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.psi.PsiElement
import org.rust.ide.inspections.fixes.SubstituteTextFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.consts.asBool
import org.rust.lang.utils.evaluation.evaluate

/**
 * Detects redundant `else` statements preceded by an irrefutable pattern.
 * Quick fix: Remove `else`
 */
class RsRedundantElseInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Redundant else"

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsVisitor() {

            override fun visitElseBranch(expr: RsElseBranch) = visitElseOrLetElseBranch(expr)

            override fun visitLetElseBranch(expr: RsLetElseBranch) = visitElseOrLetElseBranch(expr)

            private fun visitElseOrLetElseBranch(expr: RsElement) {
                when {
                    expr is RsElseBranch && !expr.isRedundant -> return
                    expr is RsLetElseBranch && !expr.isRedundant -> return
                }

                val elseExpr = when (expr) {
                    is RsElseBranch -> expr.`else`
                    is RsLetElseBranch -> expr.`else`
                    else -> return
                }

                holder.registerProblem(
                    expr,
                    elseExpr.textRangeInParent,
                    "Redundant `else`",
                    SubstituteTextFix.delete(
                        "Remove `else`",
                        expr.containingFile,
                        expr.rangeWithPrevSpace
                    )
                )
            }
        }

    companion object {
        private val RsElseBranch.isRedundant: Boolean
            get() {
                val set = mutableSetOf<RsCondition>()
                var candidate: PsiElement = this

                while (candidate is RsElseBranch || candidate is RsIfExpr) {
                    candidate.leftSiblings.filterIsInstance<RsCondition>().forEach { set.add(it) }
                    candidate = candidate.parent
                }

                return set.any { it.isRedundant }
            }

        private val RsLetElseBranch.isRedundant: Boolean
            get() = (parent as? RsLetDecl)?.pat?.isIrrefutable == true

        private val RsCondition.isRedundant: Boolean
            get() {
                val patList = patList
                return if (patList != null) {
                    patList.all { pat -> pat.isIrrefutable }
                } else {
                    val expr = expr ?: return false
                    expr.evaluate().asBool() == true
                }
            }
    }
}
