package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.util.Key
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

class MLIRCompositeProcessHandler(val handlers: List<ProcessHandler>, val showAllProcessesOutput: Boolean) : ProcessHandler() {
    private val terminatedCount = AtomicInteger()

    private inner class EachListener(val showStdOut: Boolean, val showSystem: Boolean) : ProcessListener {

        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            if (!showSystem && outputType == ProcessOutputType.SYSTEM) return
            if (showStdOut || outputType != ProcessOutputType.STDOUT || showAllProcessesOutput) {
                this@MLIRCompositeProcessHandler.notifyTextAvailable(event.text, outputType)
            }
        }

        override fun processTerminated(event: ProcessEvent) {
            val terminated = terminatedCount.incrementAndGet()
            if (terminated == handlers.size) {
                this@MLIRCompositeProcessHandler.notifyProcessTerminated(0)
            }
        }
    }

    init {
        assert(handlers.isNotEmpty())
        handlers.forEachIndexed { i, handler ->
            handler.addProcessListener(EachListener(i == handlers.lastIndex, i == 0))
        }
    }

    override fun startNotify() {
        super.startNotify()
        handlers.forEach { it.startNotify() }
    }

    override fun destroyProcessImpl() {
        handlers.forEach { it.destroyProcess() }
    }

    override fun detachProcessImpl() {
        handlers.forEach { it.detachProcess() }
    }

    override fun detachIsDefault(): Boolean = false
    override fun getProcessInput(): OutputStream? = null
}