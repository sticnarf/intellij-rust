/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

import com.intellij.openapi.util.NlsContexts.ListItem
import org.rust.ide.icons.RsIcons
import javax.swing.Icon

sealed class RsProjectTemplate(@Suppress("UnstableApiUsage") @ListItem val name: String, val isBinary: Boolean, val icon: Icon) {
    fun validateProjectName(crateName: String): String? = RsPackageNameValidator.validate(crateName, isBinary)
}

sealed class RsGenericTemplate(@Suppress("UnstableApiUsage") @ListItem name: String, isBinary: Boolean) : RsProjectTemplate(name, isBinary, RsIcons.RUST) {
    object CargoBinaryTemplate : RsGenericTemplate("Binary (application)", true)
    object CargoLibraryTemplate : RsGenericTemplate("Library", false)
}

open class RsCustomTemplate(
    @Suppress("UnstableApiUsage") @ListItem name: String,
    val url: String
) : RsProjectTemplate(name, false, RsIcons.CARGO_GENERATE) {
    object ProcMacroTemplate : RsCustomTemplate("Procedural Macro", "https://github.com/intellij-rust/rust-procmacro-quickstart-template")
    object WasmPackTemplate : RsCustomTemplate("WebAssembly Lib", "https://github.com/intellij-rust/wasm-pack-template")

    val shortLink: String
        get() = url.substringAfter("//")
}
