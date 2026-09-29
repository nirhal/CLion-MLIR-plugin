package org.komlir.intellijmlirplugin.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project

/** Optional bridge: the path-based integration never loads CLion classes. */
interface MLIRLanguageServerCMakeSupport {
    data class Target(val name: String, val profiles: List<String>)
    fun targets(project: Project): List<Target>
    fun prepare(project: Project, options: MLIRLanguageServerSettings.Options,
                build: Boolean, indicator: ProgressIndicator): GeneralCommandLine
    fun subscribe(project: Project, parent: Disposable, changed: () -> Unit)

    companion object {
        val EP = ExtensionPointName.create<MLIRLanguageServerCMakeSupport>(
            "org.komlir.intelliJ-MLIR-plugin.languageServerCMakeSupport")
        fun get() = EP.extensionList.firstOrNull()
    }
}
