/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.RsToolchainBase
import com.intellij.util.SmartList
import org.rust.lang.core.macros.*
import org.rust.lang.core.macros.errors.ProcMacroExpansionError
import org.rust.lang.core.macros.errors.ProcMacroExpansionError.ExecutableNotFound
import org.rust.lang.core.macros.errors.ProcMacroExpansionError.ProcMacroExpansionIsDisabled
import org.rust.lang.core.macros.tt.MappedSubtree
import org.rust.lang.core.macros.tt.TokenTree
import org.rust.lang.core.macros.tt.parseSubtree
import org.rust.lang.core.macros.tt.toMappedText
import org.rust.lang.core.parser.createRustPsiBuilder
import org.rust.openapiext.RsPathManager.INTELLIJ_RUST_NATIVE_HELPER
import org.rust.stdext.RsResult
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok
import java.io.IOException
import java.util.concurrent.TimeoutException

class ProcMacroExpander(
    private val project: Project,
    private val toolchain: RsToolchainBase? = project.toolchain,
    private val server: ProcMacroServerPool? = toolchain?.let { ProcMacroApplicationService.getInstance().getServer(it) },
    private val timeout: Long = Registry.get("org.rust.macros.proc.timeout").asInteger().toLong(),
) : MacroExpander<RsProcMacroData, ProcMacroExpansionError>() {
    private val isEnabled: Boolean = if (server != null) true else ProcMacroApplicationService.isEnabled()

    override fun expandMacroAsTextWithErr(
        def: RsProcMacroData,
        call: RsMacroCallData
    ): RsResult<Pair<CharSequence, RangeMap>, ProcMacroExpansionError> {
        val (macroCallBodyText, attrText) = when (val macroBody = call.macroBody) {
            is MacroCallBody.Attribute -> macroBody.item to macroBody.attr
            is MacroCallBody.Derive -> MappedText.single(macroBody.item, 0) to null
            is MacroCallBody.FunctionLike -> MappedText.single(macroBody.text, 0) to null
        }
        val (macroCallBodyLowered, rangesLowering) = project
            .createRustPsiBuilder(macroCallBodyText.text)
            .lowerDocCommentsToPsiBuilder(project)
        val loweredMacroCallBodyRanges = macroCallBodyText.ranges.mapAll(rangesLowering)
        val (macroCallBodyTt, macroCallBodyTokenMap) = macroCallBodyLowered.parseSubtree()

        val (attrSubtree, mergedTokenMap, mergedRanges) = if (attrText != null) {
            val startOffset = (loweredMacroCallBodyRanges.ranges.maxByOrNull { it.dstOffset }?.dstEndOffset ?: -1) + 1
            val shiftedRanges = attrText.ranges.ranges.map { it.dstShiftRight(startOffset) }
            val (subtree, map) = project.createRustPsiBuilder(attrText.text).parseSubtree(startOffset, macroCallBodyTokenMap.map.size)
            Triple(
                subtree.copy(delimiter = null),
                macroCallBodyTokenMap.merge(map),
                RangeMap.from(SmartList(loweredMacroCallBodyRanges.ranges + shiftedRanges))
            )
        } else {
            Triple(null, macroCallBodyTokenMap, loweredMacroCallBodyRanges)
        }
        val lib = def.artifact.path.toString()
        val env = call.packageEnv
        return expandMacroAsTtWithErr(macroCallBodyTt, attrSubtree, def.name, lib, env).map {
            val (text, ranges) = MappedSubtree(it, mergedTokenMap).toMappedText()
            text to mergedRanges.mapAll(ranges)
        }
    }

    fun expandMacroAsTtWithErr(
        macroCallBody: TokenTree.Subtree,
        attributes: TokenTree.Subtree?,
        macroName: String,
        lib: String,
        env: Map<String, String> = emptyMap()
    ): RsResult<TokenTree.Subtree, ProcMacroExpansionError> {
        val remoteLib = toolchain?.toRemotePath(lib) ?: lib
        val server = server ?: return Err(if (isEnabled) ExecutableNotFound else ProcMacroExpansionIsDisabled)
        val envList = env.map { listOf(it.key, toolchain?.toRemotePath(it.value) ?: it.value) }
        val response = try {
            server.send(Request.ExpansionMacro(macroCallBody, macroName, attributes, remoteLib, envList), timeout)
        } catch (ignored: TimeoutException) {
            return Err(ProcMacroExpansionError.Timeout(timeout))
        } catch (e: ProcessCreationException) {
            MACRO_LOG.warn("Failed to run `$INTELLIJ_RUST_NATIVE_HELPER` process", e)
            return Err(ProcMacroExpansionError.CantRunExpander)
        } catch (e: ProcessAbortedException) {
            return Err(ProcMacroExpansionError.ProcessAborted(e.exitCode))
        } catch (e: IOException) {
            MACRO_LOG.error("Error communicating with `$INTELLIJ_RUST_NATIVE_HELPER` process", e)
            return Err(ProcMacroExpansionError.IOExceptionThrown)
        }
        return when (response) {
            is Response.ExpansionMacro -> Ok(response.expansion)
            is Response.Error -> Err(ProcMacroExpansionError.ServerSideError(response.message))
        }
    }

    companion object {
        const val EXPANDER_VERSION: Int = 1
    }
}
