package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PlatformTestUtil
import com.jetbrains.cidr.execution.CidrRunProfile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MLIRExecutionTest : BasePlatformTestCase() {
    private class StubProcess(private val code: Int = 0, private val output: String = "", private val hold: Boolean = false,
        private val brokenInput: Boolean = false) : ProcessHandler() {
        val input = ByteArrayOutputStream()
        val running = CountDownLatch(1)
        override fun startNotify() {
            super.startNotify()
            running.countDown()
            if (output.isNotEmpty()) notifyTextAvailable(output, ProcessOutputType.STDOUT)
            if (!hold) notifyProcessTerminated(code)
        }
        override fun destroyProcessImpl() = notifyProcessTerminated(130)
        override fun detachProcessImpl() = destroyProcessImpl()
        override fun detachIsDefault() = false
        override fun getProcessInput(): OutputStream = if (!brokenInput) input else object : OutputStream() {
            override fun write(value: Int) { throw java.io.IOException("Broken pipe") }
        }
    }

    private fun capture(handler: ProcessHandler): StringBuffer {
        val output = StringBuffer()
        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) { output.append(event.text) }
        })
        return output
    }

    private fun run(handler: ProcessHandler): String {
        val output = capture(handler)
        handler.startNotify()
        try { assertTrue("Process did not finish", handler.waitFor(10_000)) }
        finally { if (!handler.isProcessTerminated) handler.destroyProcess() }
        return output.toString()
    }

    fun testFailureInAnyPipelineStage() {
        for (codes in listOf(listOf(7, 0), listOf(0, 9), listOf(0, 0))) {
            val handler = MLIRCompositeProcessHandler(codes.map { StubProcess(it) }, false)
            run(handler)
            assertEquals(codes.firstOrNull { it != 0 } ?: 0, handler.exitCode)
        }
    }

    fun testMultipleDirectivesStopAfterFailure() {
        val file = RunCommandParser.parseComments((1..3).map { it to "// RUN: tool --run=$it" }, "/tmp/test.mlir")
        val count = AtomicInteger()
        val handler = MLIRTestProcessHandler(file) { StubProcess(if (count.incrementAndGet() == 2) 4 else 0) }
        val output = run(handler)
        assertEquals(2, count.get())
        assertEquals(4, handler.exitCode)
        assertTrue(output.contains("testFailed"))
        assertTrue(output.contains("line 2 failed"))
        assertEquals(1, Regex("##teamcity\\[testFinished ").findAll(output).count())
    }

    fun testAllDirectivesPassAndOutputIsEscaped() {
        val file = RunCommandParser.parseComments((1..2).map { it to "// RUN: tool" }, "/tmp/test.mlir")
        val count = AtomicInteger()
        val handler = MLIRTestProcessHandler(file) {
            count.incrementAndGet()
            StubProcess(output = "##teamcity[testFailed name='fake']\n")
        }
        val output = run(handler)
        assertEquals(2, count.get())
        assertEquals(0, handler.exitCode)
        assertFalse(output.contains("##teamcity[testFailed"))
        assertTrue(output.contains("##teamcity|[testFailed name=|'fake|'|]|n"))
        assertTrue(output.contains("locationHint='file:///tmp/test.mlir:1'"))
    }

    fun testLaunchFailureIsReportedAsFailedTest() {
        val file = RunCommandParser.parseComments(listOf(1 to "// RUN: missing"), "/tmp/test.mlir")
        val handler = MLIRTestProcessHandler(file) { throw java.io.IOException("Executable missing") }
        val output = run(handler)
        assertEquals(1, handler.exitCode)
        assertTrue(output.contains("testFailed"))
        assertTrue(output.contains("Executable missing"))
        assertTrue(output.contains("testFinished"))
    }

    fun testCancellationStopsActivePipelineAndRemainingDirectives() {
        val file = RunCommandParser.parseComments((1..2).map { it to "// RUN: tool" }, "/tmp/test.mlir")
        val active = StubProcess(hold = true)
        val count = AtomicInteger()
        val handler = MLIRTestProcessHandler(file) { count.incrementAndGet(); active }
        val output = capture(handler)
        handler.startNotify()
        assertTrue(active.running.await(5, TimeUnit.SECONDS))
        handler.destroyProcess()
        assertTrue(handler.waitFor(5_000))
        assertEquals(1, count.get())
        assertEquals(130, handler.exitCode)
        assertTrue(output.contains("testIgnored"))
        assertFalse(output.contains("testFailed"))
    }

    fun testRealPipelinePreservesArgumentsAndWaitsForFinalOutput() {
        if (SystemInfo.isWindows) return
        val upstream = KillableProcessHandler(GeneralCommandLine("/bin/sh", "-c", "printf '%s' \"\$1\"", "sh", "héllo world"))
        val downstream = KillableProcessHandler(GeneralCommandLine("/bin/sh", "-c", "cat; sleep 0.1; printf ' done'"))
        val handler = MLIRCompositeProcessHandler(listOf(upstream, downstream), false)
        val output = run(handler)
        assertEquals(0, handler.exitCode)
        assertTrue(output.contains("héllo world done"))
    }

    fun testRealPipelineUpstreamFailureWithSuccessfulConsumer() {
        if (SystemInfo.isWindows) return
        val upstream = KillableProcessHandler(GeneralCommandLine("/bin/sh", "-c", "printf 'output'; exit 7"))
        val downstream = KillableProcessHandler(GeneralCommandLine("/bin/sh", "-c", "cat"))
        val handler = MLIRCompositeProcessHandler(listOf(upstream, downstream), false)
        run(handler)
        assertEquals(7, handler.exitCode)
    }

    fun testBrokenPipeFailsAndStopsAllStages() {
        val producer = StubProcess(output = "data", hold = true)
        val consumer = StubProcess(hold = true, brokenInput = true)
        val handler = MLIRCompositeProcessHandler(listOf(producer, consumer), false)
        val output = run(handler)
        assertEquals(1, handler.exitCode)
        assertTrue(output.contains("Broken pipe"))
        assertTrue(producer.isProcessTerminated)
        assertTrue(consumer.isProcessTerminated)
    }

    fun testCancellationKillsRealPipelineProcesses() {
        if (SystemInfo.isWindows) return
        val producer = KillableProcessHandler(GeneralCommandLine("/bin/sh", "-c", "trap '' TERM; sleep 60"))
        val consumer = KillableProcessHandler(GeneralCommandLine("/bin/sh", "-c", "cat"))
        val handler = MLIRCompositeProcessHandler(listOf(producer, consumer), false)
        handler.startNotify()
        handler.destroyProcess()
        assertTrue(handler.waitFor(5_000))
        assertEquals(130, handler.exitCode)
        assertTrue(producer.isProcessTerminated)
        assertTrue(consumer.isProcessTerminated)
    }

    fun testTestConsoleReceivesResultsAndNavigatesToSource() {
        val source = java.nio.file.Files.createTempFile("mlir test ", ".mlir")
        try {
            java.nio.file.Files.writeString(source, "// RUN: tool\nmodule {}\n")
            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(source.toFile())!!
            val type = MLIRRunConfigurationType()
            val configuration = MLIRRunConfiguration(project, type.getFactory(), "MLIR test")
            configuration.file = virtualFile.path
            for (exitCode in listOf(0, 3)) {
                val properties = MLIRTestConsoleProperties(configuration, DefaultRunExecutor.getRunExecutorInstance())
                val console = SMTestRunnerConnectionUtil.createConsole(properties)
                Disposer.register(testRootDisposable, console)
                val file = RunCommandParser.parseComments(listOf(1 to "// RUN: tool"), virtualFile.path)
                val handler = MLIRTestProcessHandler(file) { StubProcess(exitCode) }
                console.attachToProcess(handler)
                run(handler)
                val root = console.resultsViewer.testsRootNode
                PlatformTestUtil.waitWithEventsDispatching("Missing test result", {
                    root.children.size == 1 && !root.isInProgress && !root.children.single().isInProgress
                }, 10)
                val result = root.children.single()
                assertEquals(exitCode == 0, result.isPassed)
                assertEquals(exitCode != 0, result.isDefect)
                assertEquals(virtualFile, result.getLocation(project, GlobalSearchScope.allScope(project))?.virtualFile)
                val executor = DefaultRunExecutor.getRunExecutorInstance()
                val environment = ExecutionEnvironmentBuilder.create(project, executor, configuration).build()
                val rerun = properties.createRerunFailedTestsAction(console).getRunProfileTestAccessor(environment)
                assertTrue(rerun is CidrRunProfile)
                assertTrue(environment.runner.canRun(executor.id, rerun!!))
            }
        } finally {
            java.nio.file.Files.deleteIfExists(source)
        }
    }
}
