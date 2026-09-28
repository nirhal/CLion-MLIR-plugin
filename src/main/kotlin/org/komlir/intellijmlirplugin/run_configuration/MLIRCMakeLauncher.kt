package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.util.Key
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.cidr.cpp.execution.CMakeLauncher
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

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

    private fun pipeAllHandlers(handlers: List<ProcessHandler>) {
        handlers.forEachIndexed { idx, handler ->
            val nextHandler = handlers.getOrNull(idx + 1)
            handler.addProcessListener(object : ProcessListener {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    if (outputType == ProcessOutputType.STDOUT && nextHandler != null) {
                        try {
                            nextHandler.processInput?.write(event.text.encodeToByteArray())
                            nextHandler.processInput?.flush()
                        } catch (e: IOException) {
                            handler.notifyTextAvailable("Pipeline write failed: ${e.message}\n", ProcessOutputType.STDERR)
                            handlers.forEach { it.destroyProcess() }
                        }
                    }
                }

                override fun processTerminated(event: ProcessEvent) {
                    try { nextHandler?.processInput?.close() } catch (_: IOException) { }
                }
            })
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
        val handlers = listOf(mainProcessHandler) + pipedHandlers
        pipeAllHandlers(handlers)
        if (pipedHandlers.isNotEmpty()) {
            pipedHandlers.last().addProcessListener(object : ProcessListener {
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
        val childrenStarted = AtomicBoolean()
        fun startChildren() {
            if (!childrenStarted.compareAndSet(false, true)) return
            pipedHandlers.asReversed().forEach { it.startNotify() }
            if (configuration.testFile!!.pipelines.size > 1) {
                mainProcessHandler.notifyTextAvailable("Debug runs the first RUN directive only. Use Run to test all directives.\n", ProcessOutputType.SYSTEM)
            }
        }
        mainProcessHandler.addProcessListener(object : ProcessListener {
            override fun startNotified(event: ProcessEvent) {
                startChildren()
            }

            override fun processNotStarted() {
                startChildren()
                pipedHandlers.forEach { it.killProcess() }
            }

            override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
                if (willBeDestroyed) pipedHandlers.forEach { it.killProcess() }
            }
        })

        if (mainProcessHandler.isStartNotified) startChildren()

        return debugProcess
    }

}
