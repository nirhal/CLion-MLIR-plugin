package org.komlir.intellijmlirplugin.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.*
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem
import org.komlir.intellijmlirplugin.MLIRIcons

class MLIRLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerSupportProvider.LspServerStarter) {
        if (file.extension == "mlir") project.service<MLIRLanguageServerService>().fileOpened(serverStarter)
    }

    override fun createLspServerWidgetItem(lspServer: LspServer, currentFile: VirtualFile?) =
        LspServerWidgetItem(lspServer, currentFile, MLIRIcons.FILE, MLIRLanguageServerConfigurable::class.java)
}

internal class MLIRLspServerDescriptor(project: Project, private val command: GeneralCommandLine) :
    ProjectWideLspServerDescriptor(project, "MLIR") {
    override fun isSupportedFile(file: VirtualFile) = file.extension == "mlir"
    override fun getLanguageId(file: VirtualFile) = "mlir"
    override fun createCommandLine(): GeneralCommandLine = CommandCopy(command)
    private class CommandCopy(source: GeneralCommandLine) : GeneralCommandLine(source)
}
