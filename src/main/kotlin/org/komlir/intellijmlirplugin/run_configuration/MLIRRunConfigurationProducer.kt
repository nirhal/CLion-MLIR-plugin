package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiDirectory
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil
import org.komlir.intellijmlirplugin.MLIRLanguage

class MLIRRunConfigurationProducer : LazyRunConfigurationProducer<MLIRRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        return MLIRRunConfigurationType().getFactory()
    }

    override fun setupConfigurationFromContext(
        configuration: MLIRRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement?>
    ): Boolean {
        val element = context.psiLocation ?: return false
        if (element is PsiDirectory) {
            if (ProjectFileIndex.getInstance(configuration.project).isExcluded(element.virtualFile)) return false
            configuration.file = element.virtualFile.path
            configuration.name = "MLIR tests in ${element.name}"
            configuration.recursive = true
            return true
        }
        if (element.containingFile == null) return false
        if (element.containingFile.language != MLIRLanguage) return false
        if (PsiTreeUtil.findChildrenOfType(element.containingFile, PsiComment::class.java)
                .none { RunCommandParser.extractCommand(it) != null }) return false
        val file = element.containingFile.virtualFile
        configuration.name = file.name
        configuration.file = file.canonicalPath
        configuration.updateSettings()
        // Keep invalid directives runnable so configuration validation can explain the error.
        return true
    }

    override fun isConfigurationFromContext(
        configuration: MLIRRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val element = context.psiLocation
        val path = (element as? PsiDirectory)?.virtualFile?.path
            ?: element?.containingFile?.virtualFile?.canonicalPath
        return configuration.file != null && configuration.file == path
    }

}
