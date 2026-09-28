package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.openapi.util.Key
import java.io.OutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Owns a pipeline until every process has exited and drained its output. */
class MLIRCompositeProcessHandler(
    val handlers: List<ProcessHandler>,
    private val showAllProcessesOutput: Boolean,
) : ProcessHandler() {
    private val exitCodes = ConcurrentHashMap<Int, Int>()
    private val cancelled = AtomicBoolean()
    private val pipeFailed = AtomicBoolean()
    private val completed = AtomicBoolean()
    @Volatile var failureDescription: String? = null
        private set

    private inner class EachListener(val index: Int) : ProcessListener {

        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            if (outputType == ProcessOutputType.STDOUT && index < handlers.lastIndex) {
                try {
                    val input = handlers[index + 1].processInput ?: throw IOException("Process has no standard input")
                    input.write(event.text.toByteArray(Charsets.UTF_8))
                    input.flush()
                } catch (e: IOException) {
                    if (!cancelled.get() && pipeFailed.compareAndSet(false, true)) {
                        failureDescription = "Cannot write pipeline stage ${index + 2}: ${e.message}"
                        notifyTextAvailable("${failureDescription}\n", ProcessOutputType.STDERR)
                        stopProcesses()
                    }
                }
            }
            if (index != 0 && outputType == ProcessOutputType.SYSTEM) return
            if (index == handlers.lastIndex || outputType != ProcessOutputType.STDOUT || showAllProcessesOutput) {
                this@MLIRCompositeProcessHandler.notifyTextAvailable(event.text, outputType)
            }
        }

        override fun processTerminated(event: ProcessEvent) {
            try { handlers.getOrNull(index + 1)?.processInput?.close() } catch (_: IOException) { }
            exitCodes[index] = event.exitCode
            if (exitCodes.size == handlers.size && completed.compareAndSet(false, true)) {
                val failed = handlers.indices.firstOrNull { exitCodes[it] != 0 }
                if (failed != null && failureDescription == null) {
                    failureDescription = "Pipeline stage ${failed + 1} exited with code ${exitCodes[failed]}"
                }
                val exitCode = when {
                    cancelled.get() -> 130
                    pipeFailed.get() -> 1
                    failed != null -> exitCodes.getValue(failed)
                    else -> 0
                }
                this@MLIRCompositeProcessHandler.notifyProcessTerminated(exitCode)
            }
        }
    }

    init {
        require(handlers.isNotEmpty())
        handlers.forEachIndexed { i, handler ->
            handler.addProcessListener(EachListener(i))
        }
    }

    override fun startNotify() {
        super.startNotify()
        handlers.asReversed().forEach { it.startNotify() }
        try { handlers.first().processInput?.close() } catch (_: IOException) { }
    }

    override fun destroyProcessImpl() {
        cancelled.set(true)
        stopProcesses()
    }

    private fun stopProcesses() {
        handlers.forEach {
            if (it is KillableProcessHandler) it.killProcess() else it.destroyProcess()
        }
    }

    override fun detachProcessImpl() {
        // A test owns all its processes; detaching must not leave a pipeline running.
        destroyProcessImpl()
    }

    override fun detachIsDefault(): Boolean = false
    override fun getProcessInput(): OutputStream? = null
}
