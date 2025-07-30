package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.util.SystemInfo

class MLIRRunProfileState(
    private val environment: ExecutionEnvironment,
    private val config: MLIRRunConfiguration
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val shell = if (SystemInfo.isWindows) "cmd" else "sh"
        val shellFlag = if (SystemInfo.isWindows) "/c" else "-c"
        val commandLine = GeneralCommandLine(shell)
            .withParameters(shellFlag)
            .withParameters(config.command ?: "")
            .withWorkDirectory(config.workingDir)
        return KillableProcessHandler(commandLine)
    }
}