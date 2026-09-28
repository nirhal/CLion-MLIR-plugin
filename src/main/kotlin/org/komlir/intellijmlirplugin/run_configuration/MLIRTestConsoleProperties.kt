package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.Executor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.module.Module
import com.jetbrains.cidr.execution.CidrRunProfile

class MLIRTestConsoleProperties(
    private val configuration: MLIRRunConfiguration,
    executor: Executor,
) : SMTRunnerConsoleProperties(configuration, "MLIR", executor) {
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
