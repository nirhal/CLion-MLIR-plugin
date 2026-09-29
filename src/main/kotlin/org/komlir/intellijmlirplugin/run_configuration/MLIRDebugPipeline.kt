package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.process.*
import com.intellij.openapi.util.Key
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Keeps downstream tools alive until debugger output reaches EOF, unless the user stops the run. */
internal object MLIRDebugPipeline {
    fun connect(mainProcessHandler: ProcessHandler, pipedHandlers: List<KillableProcessHandler>,
                hasMoreDirectives: Boolean = false) {
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
            if (hasMoreDirectives) {
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
                // CLion also destroys its debugger handler after a normal inferior exit.
                // Only an explicit IDE Stop request should cancel downstream tools.
                if (mainProcessHandler.getUserData(ProcessHandler.TERMINATION_REQUESTED) == true) {
                    pipedHandlers.forEach { it.killProcess() }
                }
            }
        })

        if (mainProcessHandler.isStartNotified) startChildren()

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
}
