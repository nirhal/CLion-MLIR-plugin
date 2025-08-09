package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration
import com.jetbrains.cidr.cpp.execution.CMakeRunConfigurationType
import org.komlir.intellijmlirplugin.MLIRIcons
import javax.swing.Icon

class MLIRRunConfigurationType() : CMakeRunConfigurationType(
    "MLIR_RUN_CONFIGURATION",
    "MLIR_RUN_CONFIGURATION",
    "MLIR File",
    "Runs MLIR files",
    NotNullLazyValue<Icon>.createConstantValue(MLIRIcons.FILE)
) {

    override fun getIcon(): Icon? = MLIRIcons.FILE

    override fun createRunConfiguration(
        project: Project,
        configurationFactory: ConfigurationFactory
    ): CMakeAppRunConfiguration {
        return MLIRRunConfiguration(project, configurationFactory, "MLIR Run Configuration")
    }

    override fun createEditor(p0: Project): SettingsEditor<out CMakeAppRunConfiguration?>? {
        return MLIRRunConfigurationEditor()
    }

}