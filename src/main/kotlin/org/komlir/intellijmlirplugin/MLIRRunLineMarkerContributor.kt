package org.komlir.intellijmlirplugin

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.komlir.intellijmlirplugin.run_configuration.RunCommandParser

class MLIRRunLineMarkerContributor : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        if (!isRunCommand(element)) return null

        val fileName = element.containingFile.name
        return Info(
            AllIcons.RunConfigurations.TestState.Run,
            ExecutorAction.getActions(0)
        ) { "Run $fileName" }
    }

    private fun isRunCommand(element: PsiElement): Boolean {
        return element is PsiComment && RunCommandParser.extractCommand(element) != null
    }
}
