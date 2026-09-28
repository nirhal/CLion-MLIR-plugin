package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.Executor
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.module.Module
import com.jetbrains.cidr.execution.CidrRunProfile
import com.jetbrains.cidr.execution.CidrPathWithOffsetConsoleFilter
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment
import java.nio.file.Path

class MLIRTestConsoleProperties(
    private val configuration: MLIRRunConfiguration,
    executor: Executor,
) : SMTRunnerConsoleProperties(configuration, "MLIR", executor) {
    fun createTestConsole(environment: CidrToolEnvironment? = null): SMTRunnerConsoleView {
        val workingDirectory = configuration.workingDirectory?.takeIf { it.isNotBlank() }
            ?: configuration.file?.let { java.io.File(it).parent }
        val diagnosticFilter = CidrPathWithOffsetConsoleFilter(configuration.project, environment,
            workingDirectory?.let { Path.of(it) })
        addStackTraceFilter(diagnosticFilter)
        return SMTestRunnerConnectionUtil.createConsole(this).also {
            it.addMessageFilter(diagnosticFilter)
        }
    }

    override fun createRerunFailedTestsAction(consoleView: ConsoleView): AbstractRerunFailedTestsAction =
        object : AbstractRerunFailedTestsAction(consoleView) {
            override fun getRunProfile(environment: ExecutionEnvironment): MyRunProfile =
                object : MyRunProfile(configuration), CidrRunProfile {
                    override fun getModules(): Array<Module> = emptyArray()
                    override fun getState(executor: Executor, environment: ExecutionEnvironment) =
                        configuration.getState(executor, environment)
                }
        }.apply {
            init(this@MLIRTestConsoleProperties)
            setModelProvider { (consoleView as SMTRunnerConsoleView).resultsViewer }
        }
}
