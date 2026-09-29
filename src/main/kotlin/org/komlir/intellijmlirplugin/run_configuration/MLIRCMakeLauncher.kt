package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.cidr.cpp.execution.CMakeLauncher

class MLIRCMakeLauncher(
    environment: ExecutionEnvironment,
    override val configuration: MLIRRunConfiguration,
    private val pipeline: RunCommandParser.Pipeline,
): CMakeLauncher(environment, configuration) {

    override fun usePty() = false

    private fun createPipedHandlers(): List<KillableProcessHandler> {
        val handlers = mutableListOf<KillableProcessHandler>()
        try {
            for (cmd in pipeline.commands.drop(1)) {
                val commandLine = GeneralCommandLine(cmd.executable)
                    .withParameters(cmd.arguments)
                    .withCharset(Charsets.UTF_8)
                    .withEnvironment(configuration.envs)
                    .withParentEnvironmentType(if (configuration.isPassParentEnvs) GeneralCommandLine.ParentEnvironmentType.CONSOLE
                        else GeneralCommandLine.ParentEnvironmentType.NONE)
                    .withWorkDirectory(configuration.workingDirectory)
                handlers += KillableProcessHandler(commandLine)
            }
            return handlers
        } catch (e: Exception) {
            handlers.forEach { it.startNotify(); it.killProcess() }
            handlers.forEach { it.waitFor() }
            throw e
        }
    }

    override fun createProcess(state: CommandLineState): ProcessHandler {
        val mainProcessHandler = super.createProcess(state)
        try {
            return MLIRCompositeProcessHandler(listOf(mainProcessHandler) + createPipedHandlers(), configuration.showAllProcessesOutput)
        } catch (e: Exception) {
            mainProcessHandler.startNotify()
            if (mainProcessHandler is KillableProcessHandler) mainProcessHandler.killProcess()
            else mainProcessHandler.destroyProcess()
            mainProcessHandler.waitFor()
            throw e
        }
    }

    override fun createDebugProcess(
        state: CommandLineState,
        session: XDebugSession
    ): XDebugProcess {
        val debugProcess = super.createDebugProcess(state, session)
        val mainProcessHandler = debugProcess.processHandler
        val pipedHandlers = try { createPipedHandlers() } catch (e: Exception) {
            mainProcessHandler.destroyProcess()
            throw e
        }
        MLIRDebugPipeline.connect(mainProcessHandler, pipedHandlers, configuration.testFile!!.pipelines.size > 1)

        return debugProcess
    }

}
