package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

class MLIRRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<MLIRRunConfigurationOptions>(project, factory, name) {

    var workingDir: String?
        get() = options.workingDir
        set(value) {
            options.workingDir = value
        }

    var command: String?
        get() = options.command
        set(value) {
            options.command = value
        }

    override fun getOptions(): MLIRRunConfigurationOptions {
        return super.getOptions() as MLIRRunConfigurationOptions
    }

    override fun getConfigurationEditor(): SettingsEditor<out MLIRRunConfiguration> {
        return MLIRRunConfigurationEditor()
    }

    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment
    ): RunProfileState {
        return MLIRRunProfileState(environment, this)
    }
}