package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.RunContentDescriptor

/**
 * CLion's CMake runners cast every state to CidrCommandLineState, but Run mode uses a test-runner
 * CommandLineState. This runner takes Run before them; Debug keeps CLion's runners and Cidr state.
 */
class MLIRRunProgramRunner : GenericProgramRunner<RunnerSettings>() {
    override fun getRunnerId(): String = "MLIRRunProgramRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultRunExecutor.EXECUTOR_ID && profile is MLIRRunConfiguration

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val result = state.execute(environment.executor, this) ?: return null
        return RunContentBuilder(result, environment).showRunContent(environment.contentToReuse)
    }
}