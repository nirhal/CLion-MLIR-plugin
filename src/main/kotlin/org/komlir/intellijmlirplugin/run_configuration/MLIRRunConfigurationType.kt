package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import org.komlir.intellijmlirplugin.MLIRIcons
import javax.swing.Icon

class MLIRRunConfigurationType : ConfigurationType {
    companion object {
        const val RUN_CONFIGURATION_ID = "MLIR_RUN_CONFIGURATION"
    }
    override fun getDisplayName() = "MLIR File"
    override fun getConfigurationTypeDescription() = "Runs MLIR files"
    override fun getIcon(): Icon = MLIRIcons.FILE
    override fun getId() = RUN_CONFIGURATION_ID

    val factory = object : ConfigurationFactory(this) {
        override fun createTemplateConfiguration(project: Project): RunConfiguration {
            return MLIRRunConfiguration(project, this, "MLIR Run Configuration")
        }

        override fun getId(): String = RUN_CONFIGURATION_ID

        override fun getOptionsClass(): Class<out BaseState?> {
            return MLIRRunConfigurationOptions::class.java
        }
    }

    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(factory)
}