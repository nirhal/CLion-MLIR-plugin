package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.util.Key
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.cidr.cpp.execution.CMakeLauncher

class MLIRCMakeLauncher(
    environment: ExecutionEnvironment,
    override val configuration: MLIRRunConfiguration
): CMakeLauncher(environment, configuration) {

    private fun createPipedHandlers(): List<KillableProcessHandler> {
        return configuration.pipeCommands.map { cmd ->
            val commandLine = GeneralCommandLine(cmd.executable)
                .withParameters(cmd.arguments)
                .withWorkDirectory(configuration.workingDirectory)
            KillableProcessHandler(commandLine)
        }
    }

    private fun pipeAllHandlers(handlers: List<ProcessHandler>) {
        handlers.forEachIndexed { idx, handler ->
            val nextHandler = handlers.getOrNull(idx + 1)
            handler.addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    if (outputType == ProcessOutputType.STDOUT && nextHandler != null) {
                        nextHandler.processInput?.write(event.text.encodeToByteArray())
                        nextHandler.processInput?.flush()
                    }
                }

                override fun processTerminated(event: ProcessEvent) {
                    nextHandler?.processInput?.close()
                }
            })
        }
    }
    override fun createProcess(state: CommandLineState): ProcessHandler {
        val mainProcessHandler = super.createProcess(state)
        val pipedHandlers = createPipedHandlers()
        val handlers = listOf(mainProcessHandler) + pipedHandlers
        pipeAllHandlers(handlers)
        return MLIRCompositeProcessHandler(handlers, configuration.showAllProcessesOutput)
    }

    override fun createDebugProcess(
        state: CommandLineState,
        session: XDebugSession
    ): XDebugProcess {
        val debugProcess = super.createDebugProcess(state, session)
        val mainProcessHandler = debugProcess.processHandler
        val pipedHandlers = createPipedHandlers()
        val handlers = listOf(mainProcessHandler) + pipedHandlers
        pipeAllHandlers(handlers)
        if (pipedHandlers.isNotEmpty()) {
            pipedHandlers.last().addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    val targetOutputType = when(outputType) {
                        ProcessOutputType.STDERR -> ProcessOutputType.STDERR
                        ProcessOutputType.STDOUT -> ProcessOutputType.SYSTEM // Printing output to SYSTEM to avoid an infinite loop
                        else -> return
                    }
                    mainProcessHandler.notifyTextAvailable(event.text, targetOutputType)
                }
            })
        }
        mainProcessHandler.addProcessListener(object : ProcessAdapter() {
            override fun startNotified(event: ProcessEvent) {
                pipedHandlers.forEach { it.startNotify() }
            }

            override fun processNotStarted() {
                pipedHandlers.forEach { it.killProcess() }
            }

            override fun processTerminated(event: ProcessEvent) {
                pipedHandlers.forEach { it.destroyProcess() }
            }
        })

        return debugProcess
    }

}