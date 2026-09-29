package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Files run sequentially; a failed directive stops its file, not the rest of the suite. */
class MLIRTestProcessHandler(
    private val tests: List<MLIRTestDiscovery.TestCase>,
    private val suiteName: String? = null,
    private val launch: (MLIRTestDiscovery.TestCase, RunCommandParser.Pipeline) -> ProcessHandler,
) : ProcessHandler() {
    constructor(test: RunCommandParser.TestFile, launch: (RunCommandParser.Pipeline) -> ProcessHandler) :
        this(listOf(MLIRTestDiscovery.TestCase(test.path, java.io.File(test.path).name, test)), null,
            { _, pipeline -> launch(pipeline) })

    private val cancelled = AtomicBoolean()
    private val started = AtomicBoolean()
    @Volatile private var active: ProcessHandler? = null

    override fun startNotify() {
        super.startNotify()
        if (started.compareAndSet(false, true)) AppExecutorUtil.getAppExecutorService().execute { runTests() }
    }

    private fun runTests() {
        var code = 0
        try {
            message("testCount", "count" to tests.size.toString())
            suiteName?.let { message("testSuiteStarted", "name" to it) }
            for (test in tests) {
                val result = runTest(test)
                if (code == 0) code = result
            }
        } finally {
            suiteName?.let { message("testSuiteFinished", "name" to it) }
            notifyProcessTerminated(if (cancelled.get()) 130 else code)
        }
    }

    private fun runTest(testCase: MLIRTestDiscovery.TestCase): Int {
        val testName = testCase.name
        val test = testCase.test
        val start = System.nanoTime()
        var code = 0
        message("testStarted", "name" to testName, "locationHint" to "file://${testCase.path}:${test?.pipelines?.first()?.line ?: 1}")
        try {
            if (!cancelled.get() && testCase.error != null) {
                code = 1
                message("testFailed", "name" to testName, "message" to "Invalid MLIR test", "details" to testCase.error)
            }
            for (pipeline in test?.pipelines.orEmpty()) {
                if (cancelled.get()) break
                message("testStdOut", "name" to testName, "out" to "RUN (line ${pipeline.line}): ${pipeline.source}\n")
                val handler = launch(testCase, pipeline)
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
            // Terminate the aggregate handler only after all files have reported their results.
        }
        return code
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
