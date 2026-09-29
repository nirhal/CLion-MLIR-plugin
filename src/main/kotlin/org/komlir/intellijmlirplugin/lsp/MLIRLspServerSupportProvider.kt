package org.komlir.intellijmlirplugin.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.*
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem
import com.intellij.platform.lsp.api.customization.LspCompletionSupport
import com.intellij.psi.util.PsiTreeUtil
import org.komlir.intellijmlirplugin.MLIRIcons
import org.komlir.intellijmlirplugin.psi.MLIROperationElement
import org.komlir.intellijmlirplugin.psi.MLIROperationCompletionItem
import com.intellij.codeInsight.lookup.LookupElement
import org.eclipse.lsp4j.CompletionItem

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
    override val lspCompletionSupport = object : LspCompletionSupport() {
        override fun createLookupElement(parameters: CompletionParameters, item: CompletionItem): LookupElement? {
            val lookup = super.createLookupElement(parameters, item) ?: return null
            return if (PsiTreeUtil.getParentOfType(parameters.position, MLIROperationElement::class.java, false) != null)
                MLIROperationCompletionItem(lookup, fromLanguageServer = true) else lookup
        }

        override fun getCompletionPrefix(parameters: CompletionParameters, defaultPrefix: String): String {
            // MLIR returns operation names without the dialect and without a textEdit.
            // Our PSI reference spans the full name; matching/insertion must keep the dialect.
            if (PsiTreeUtil.getParentOfType(parameters.position, MLIROperationElement::class.java, false) != null) {
                return defaultPrefix.substringAfter('.', defaultPrefix)
            }
            return defaultPrefix
        }
    }
    override fun createCommandLine(): GeneralCommandLine = CommandCopy(command)
    private class CommandCopy(source: GeneralCommandLine) : GeneralCommandLine(source)
}
