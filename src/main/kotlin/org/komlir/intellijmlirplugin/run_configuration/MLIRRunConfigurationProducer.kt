package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
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
        return configuration.file != null && configuration.file == context.psiLocation?.containingFile?.virtualFile?.canonicalPath
    }

}
