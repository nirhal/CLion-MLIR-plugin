package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.OutputStream

class MLIRDebugPipelineTest : BasePlatformTestCase() {
    /** CLion uses destroyProcess even when the inferior exits normally. */
    private class DebuggerHandler(private val code: Int = 0) : ProcessHandler() {
        override fun destroyProcessImpl() = notifyProcessTerminated(code)
        override fun detachProcessImpl() = destroyProcessImpl()
        override fun detachIsDefault() = false
        override fun getProcessInput(): OutputStream? = null
    }

    fun testNormalDebuggerShutdownAllowsDelayedDiagnosticsAfterEof() {
        if (SystemInfo.isWindows) return
        for (exitCode in listOf(0, 7)) {
            val debugger = DebuggerHandler(exitCode)
            // Like FileCheck, read the complete input before emitting a failure diagnostic.
            val checker = KillableProcessHandler(GeneralCommandLine("/bin/sh", "-c",
                "cat >/dev/null; sleep 0.05; printf 'CHECK: expected string not found in input\\n' >&2; exit 1"))
            val console = ConsoleViewImpl(project, false)
            Disposer.register(testRootDisposable, console)
            console.component
            console.attachToProcess(debugger)
            MLIRDebugPipeline.connect(debugger, listOf(checker))
            try {
                debugger.startNotify()
                debugger.notifyTextAvailable("actual output\n", ProcessOutputType.STDOUT)
                debugger.destroyProcess()
                assertTrue(debugger.waitFor(5000))
                assertTrue(checker.waitFor(5000))
                assertEquals("Checker must finish normally, not be killed", 1, checker.exitCode)
                PlatformTestUtil.waitWithEventsDispatching("Missing diagnostic after debugger termination", {
                    console.flushDeferredText()
                    console.editor!!.document.text.contains("CHECK: expected string not found in input")
                }, 5)
            } finally {
                if (!checker.isProcessTerminated) checker.killProcess()
            }
        }
    }

    fun testExplicitStopCancelsDownstreamProcesses() {
        if (SystemInfo.isWindows) return
        val debugger = DebuggerHandler()
        val checker = KillableProcessHandler(GeneralCommandLine("/bin/sh", "-c",
            "cat >/dev/null; sleep 60"))
        MLIRDebugPipeline.connect(debugger, listOf(checker))
        try {
            debugger.startNotify()
            debugger.putUserData(ProcessHandler.TERMINATION_REQUESTED, true)
            debugger.destroyProcess()
            assertTrue(debugger.waitFor(5000))
            assertTrue("Stop must not leave the checker running", checker.waitFor(5000))
            assertTrue(checker.exitCode != 0)
        } finally {
            if (!checker.isProcessTerminated) checker.killProcess()
        }
    }
}
