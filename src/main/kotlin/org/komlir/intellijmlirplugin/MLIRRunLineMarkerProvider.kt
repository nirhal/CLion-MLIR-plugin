package org.komlir.intellijmlirplugin

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.jetbrains.cidr.cpp.cmake.model.CMakeConfiguration
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import org.jetbrains.plugins.terminal.TerminalToolWindowManager


class MLIRRunLineMarkerProvider: LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val configurations = findOptConfigurations(element) ?: return null
        val fileName = element.containingFile.viewProvider.virtualFile.name

        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.RunConfigurations.TestState.Run,
            { "Run $fileName" },
            { _, elt ->
                runCommandFor(elt, configurations.firstOrNull() ?: return@LineMarkerInfo )
            },
            GutterIconRenderer.Alignment.CENTER,
            { "Run $fileName" }
        )
    }

    private fun getOptConfigurations(element: PsiElement, optToolName: String): List<CMakeConfiguration> {
        val cmakeWorkspace = CMakeWorkspace.getInstance(element.project)
        return cmakeWorkspace.modelTargets.find { it.name == optToolName }?.buildConfigurations ?: emptyList()
    }

    private fun findOptConfigurations(element: PsiElement): List<CMakeConfiguration>? {
        val args = getCommand(element)?.split(" ") ?: return null
        val optToolName = args.firstOrNull() ?: return null
        return getOptConfigurations(element, optToolName)
    }

    private fun getCommand(element: PsiElement): String? {
        if (element !is PsiComment) return null
        val command = element.text.trim().removePrefix("//").trim()
        if (!command.startsWith("RUN:")) return null
        return command.removePrefix("RUN:").trim()
    }

    private fun runCommandFor(element: PsiElement, configuration: CMakeConfiguration) {
        val project = element.project
        val file = element.containingFile.viewProvider.virtualFile
        val command = getCommand(element)?.replace("%s", file.name) ?: return
        val optDir = configuration.buildWorkingDir

        val terminalToolWindowManager = TerminalToolWindowManager.getInstance(project)

        terminalToolWindowManager.createShellWidget(file.parent.canonicalPath, file.name, true, true)
            .sendCommandToExecute("${optDir.canonicalPath}/${command}")
    }
}