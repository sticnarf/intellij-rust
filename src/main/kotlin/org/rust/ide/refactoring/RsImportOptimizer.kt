/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.lang.ImportOptimizer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import org.rust.ide.inspections.lints.RsUnusedImportInspection
import org.rust.ide.inspections.lints.isUsed
import org.rust.ide.utils.import.COMPARATOR_FOR_SPECKS_IN_USE_GROUP
import org.rust.ide.utils.import.UseItemWrapper
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.stdext.withNext

class RsImportOptimizer : ImportOptimizer {
    override fun supports(file: PsiFile): Boolean = file is RsFile

    override fun processFile(file: PsiFile) = Runnable {
        val documentManager = PsiDocumentManager.getInstance(file.project)
        val document = documentManager.getDocument(file)
        if (document != null) {
            documentManager.commitDocument(document)
        }
        optimizeAndReoderUseItems(file as RsFile)
        reorderExternCrates(file)
    }

    private fun reorderExternCrates(file: RsFile) {
        val first = file.childrenOfType<RsElement>()
            .firstOrNull { it !is RsInnerAttr } ?: return
        val externCrateItems = file.childrenOfType<RsExternCrateItem>()
        externCrateItems
            .sortedBy { it.referenceName }
            .mapNotNull { it.copy() as? RsExternCrateItem }
            .forEach { file.addBefore(it, first) }

        externCrateItems.forEach { it.delete() }
    }

    private fun optimizeAndReoderUseItems(mod: RsMod) {
        val uses = mod.childrenOfType<RsUseItem>()
        if (uses.isNotEmpty()) {
            replaceOrderOfUseItems(mod, uses)
        }
        val mods = mod.childrenOfType<RsMod>()
        mods.forEach { optimizeAndReoderUseItems(it) }
    }

    fun optimizeUseItems(mod: RsMod) {
        val psiFactory = RsPsiFactory(mod.project)
        val uses = mod.childrenOfType<RsUseItem>()
        uses.forEach { optimizeUseItem(psiFactory, it) }
        val mods = mod.childrenOfType<RsMod>()
        mods.forEach { optimizeUseItems(it) }
    }

    companion object {

        fun optimizeUseItem(psiFactory: RsPsiFactory, useItem: RsUseItem) {
            val useSpeck = useItem.useSpeck ?: return
            val used = optimizeUseSpeck(psiFactory, useSpeck)
            if (!used) {
                (useItem.nextSibling as? PsiWhiteSpace)?.delete()
                useItem.delete()
            }
        }

        /** Returns false if [useSpeck] is empty and should be removed */
        fun optimizeUseSpeck(psiFactory: RsPsiFactory, useSpeck: RsUseSpeck): Boolean {
            val checkUnusedImports = RsUnusedImportInspection.isEnabled(useSpeck.project)
            return optimizeUseSpeck(psiFactory, useSpeck, checkUnusedImports)
        }

        private fun optimizeUseSpeck(
            psiFactory: RsPsiFactory,
            useSpeck: RsUseSpeck,
            checkUnusedImports: Boolean
        ): Boolean {
            val useGroup = useSpeck.useGroup
            if (useGroup == null) {
                if (!checkUnusedImports) return true

                return if (!useSpeck.isUsed()) {
                    useSpeck.deleteWithSurroundingComma()
                    false
                } else {
                    true
                }
            } else {
                useGroup.useSpeckList.forEach { optimizeUseSpeck(psiFactory, it, checkUnusedImports) }
                if (removeUseSpeckIfEmpty(useSpeck)) return false
                if (removeCurlyBracesIfPossible(psiFactory, useSpeck)) return true
                useGroup.sortUseSpecks()
                return true
            }
        }

        fun RsUseGroup.sortUseSpecks() {
            val sortedList = useSpeckList
                .sortedWith(COMPARATOR_FOR_SPECKS_IN_USE_GROUP)
                .map { it.copy() }
            useSpeckList.zip(sortedList).forEach { it.first.replace(it.second) }
        }

        /** Returns true if successfully removed, e.g. `use aaa::{bbb};` -> `use aaa::bbb;` */
        private fun removeCurlyBracesIfPossible(psiFactory: RsPsiFactory, useSpeck: RsUseSpeck): Boolean {
            val name = useSpeck.useGroup?.asTrivial?.text ?: return false
            val path = useSpeck.path?.text
            val tempPath = "${if (path != null) "$path::" else ""}$name"
            val newUseSpeck = psiFactory.createUseSpeck(tempPath)
            useSpeck.replace(newUseSpeck)
            return true
        }

        /**
         * Returns true if [useSpeck] is empty and was successfully removed,
         * e.g. `use aaa::{bbb::{}, ccc, ddd};` -> `use aaa::{ccc, ddd};`
         */
        private fun removeUseSpeckIfEmpty(useSpeck: RsUseSpeck): Boolean {
            val useGroup = useSpeck.useGroup ?: return false
            if (useGroup.useSpeckList.isNotEmpty()) return false
            if (useSpeck.parent is RsUseGroup) {
                useSpeck.deleteWithSurroundingComma()
            }
            // else can't delete useSpeck.parent if it is RsUseItem, because it will cause invalidation exception
            return true
        }

        private fun replaceOrderOfUseItems(mod: RsMod, uses: Collection<RsUseItem>) {
            // We should ignore all items before `{` in inline modules
            val offset = if (mod is RsModItem) mod.lbrace.textOffset + 1 else 0
            val first = mod.childrenOfType<RsElement>()
                .firstOrNull { it.textOffset >= offset && it !is RsExternCrateItem && it !is RsAttr } ?: return
            val psiFactory = RsPsiFactory(mod.project)
            val sortedUses = uses
                .asSequence()
                .map { UseItemWrapper(it) }
                .filter {
                    val useSpeck = it.useItem.useSpeck ?: return@filter false
                    optimizeUseSpeck(psiFactory, useSpeck)
                }
                .sorted()

            for ((useWrapper, nextUseWrapper) in sortedUses.withNext()) {
                val addedUseItem = mod.addBefore(useWrapper.useItem, first)
                mod.addAfter(psiFactory.createNewline(), addedUseItem)
                if (useWrapper.packageGroupLevel != nextUseWrapper?.packageGroupLevel) {
                    mod.addAfter(psiFactory.createNewline(), addedUseItem)
                }
            }
            uses.forEach {
                (it.nextSibling as? PsiWhiteSpace)?.delete()
                it.delete()
            }
        }
    }
}
