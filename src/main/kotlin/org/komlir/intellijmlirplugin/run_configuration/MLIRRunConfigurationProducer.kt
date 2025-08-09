package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement

class MLIRRunConfigurationProducer : LazyRunConfigurationProducer<MLIRRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        return MLIRRunConfigurationType().factory
    }

    override fun setupConfigurationFromContext(
        configuration: MLIRRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement?>
    ): Boolean {

        val element = context.psiLocation ?: return false
        if (element.containingFile == null) return false
        val file = element.containingFile.virtualFile
        configuration.name = file.name
        configuration.file = file.canonicalPath
        return configuration.updateSettings()
    }

    override fun isConfigurationFromContext(
        configuration: MLIRRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        return true
    }

}