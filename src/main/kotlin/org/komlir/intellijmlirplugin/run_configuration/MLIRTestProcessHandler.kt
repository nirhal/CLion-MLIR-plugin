package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** One file is one test. Pipelines run in order and stop at the first failure. */
class MLIRTestProcessHandler(
    private val test: RunCommandParser.TestFile,
    private val launch: (RunCommandParser.Pipeline) -> ProcessHandler,
) : ProcessHandler() {
    private val cancelled = AtomicBoolean()
    private val started = AtomicBoolean()
    @Volatile private var active: ProcessHandler? = null
    private val testName = File(test.path).name

    override fun startNotify() {
        super.startNotify()
        if (started.compareAndSet(false, true)) AppExecutorUtil.getAppExecutorService().execute { runTest() }
    }

    private fun runTest() {
        val start = System.nanoTime()
        var code = 0
        message("testStarted", "name" to testName, "locationHint" to "file://${test.path}:${test.pipelines.first().line}")
        try {
            for (pipeline in test.pipelines) {
                if (cancelled.get()) break
                message("testStdOut", "name" to testName, "out" to "RUN (line ${pipeline.line}): ${pipeline.source}\n")
                val handler = launch(pipeline)
                active = handler
                handler.addProcessListener(object : ProcessListener {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        message(if (outputType == ProcessOutputType.STDERR) "testStdErr" else "testStdOut",
                            "name" to testName, "out" to event.text)
                    }
                })
                handler.startNotify()
                if (cancelled.get()) handler.destroyProcess()
                handler.waitFor()
                active = null
                code = handler.exitCode ?: 1
                if (code != 0) {
                    if (!cancelled.get()) {
                        message("testFailed", "name" to testName,
                            "message" to "RUN at line ${pipeline.line} failed (exit $code)",
                            "details" to ((handler as? MLIRCompositeProcessHandler)?.failureDescription ?: pipeline.source))
                    }
                    break
                }
            }
        } catch (e: Exception) {
            code = 1
            active?.let { it.destroyProcess(); it.waitFor() }
            if (!cancelled.get()) message("testFailed", "name" to testName,
                "message" to "Could not execute RUN command", "details" to (e.message ?: e.javaClass.simpleName))
        } finally {
            active = null
            if (cancelled.get()) {
                code = 130
                message("testIgnored", "name" to testName, "message" to "Cancelled by user")
            }
            message("testFinished", "name" to testName, "duration" to ((System.nanoTime() - start) / 1_000_000).toString())
            notifyProcessTerminated(code)
        }
    }

    private fun message(type: String, vararg attributes: Pair<String, String>) {
        // Wrap tool output as data so it cannot be interpreted as test protocol messages.
        val fields = attributes.joinToString(" ") { (key, value) -> "$key='${escape(value)}'" }
        notifyTextAvailable("##teamcity[$type $fields]\n", ProcessOutputType.STDOUT)
    }

    override fun destroyProcessImpl() {
        cancelled.set(true)
        active?.destroyProcess()
    }

    override fun detachProcessImpl() = destroyProcessImpl()
    override fun detachIsDefault() = false
    override fun getProcessInput(): OutputStream? = null

    companion object {
        internal fun escape(value: String): String = value.replace("|", "||").replace("'", "|'")
            .replace("\n", "|n").replace("\r", "|r").replace("[", "|[").replace("]", "|]")
    }
}
