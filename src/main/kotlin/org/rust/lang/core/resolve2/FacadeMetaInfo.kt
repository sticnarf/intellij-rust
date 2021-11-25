/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.util.containers.map2Array
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve2.RsModInfoBase.RsModInfo

data class NamedItem(val name: String, val item: RsNamedElement)

/** List of items added to [context] by glob import to [this] */
fun RsMod.exportedItems(context: RsMod): List<NamedItem> {
    val info = getModInfo(this) as? RsModInfo ?: return emptyList()
    val contextInfo = getModInfo(context) as? RsModInfo ?: return emptyList()
    return info.modData
        .getVisibleItems { it.isVisibleFromMod(contextInfo.modData) }
        .flatMap { (name, perNs) ->
            perNs.allVisItems().flatMap { (visItem, namespace) ->
                val items = visItem.toPsi(info, namespace)
                items.map { NamedItem(name, it) }
            }
        }
}

private fun PerNs.allVisItems(): Array<Pair<VisItem, Namespace>> =
    types.map2Array { it to Namespace.Types } +
        values.map2Array { it to Namespace.Values } +
        macros.map2Array { it to Namespace.Macros }

/**
 * In most cases returns [RsMod.getOwnedDirectory],
 * or some its parent directory if there is complex `include!` or mod declaration with ```#[path]``` attribute.
 * Note that this directory may not contain [this]
 */
fun RsMod.getDirectoryContainedAllChildFiles(): VirtualFile? {
    val (_, _, modData) = getModInfo(this) as? RsModInfo ?: return null
    return PersistentFS.getInstance().findFileById(modData.directoryContainedAllChildFiles ?: return null)
}

fun CrateDefMap.hasTransitiveGlobImport(source: RsMod, target: RsMod): Boolean {
    val sourceModData = getModData(source) ?: return false
    val targetModData = getModData(target) ?: return false
    return globImportGraph.hasTransitiveGlobImport(sourceModData, targetModData)
}
