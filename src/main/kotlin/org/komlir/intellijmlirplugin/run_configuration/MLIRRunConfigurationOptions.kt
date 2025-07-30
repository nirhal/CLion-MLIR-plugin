package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.configurations.RunConfigurationOptions


class MLIRRunConfigurationOptions : RunConfigurationOptions() {
    private val myWorkingDir = string("").provideDelegate(this, "workingDir")
    private val myCommand = string("").provideDelegate(this, "command")

    var workingDir: String?
        get() = myWorkingDir.getValue(this)
        set(scriptName) {
            myWorkingDir.setValue(this, scriptName)
        }

    var command: String?
        get() = myCommand.getValue(this)
        set(scriptName) {
            myCommand.setValue(this, scriptName)
        }
}