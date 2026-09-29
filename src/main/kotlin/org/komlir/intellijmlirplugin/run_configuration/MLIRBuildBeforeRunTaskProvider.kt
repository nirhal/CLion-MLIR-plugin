package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.WrappingRunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.jetbrains.cidr.cpp.execution.CMakeBuildBeforeRunTaskProvider

/** Reuses CLion's build UI, cancellation, toolchains and profile handling. */
class MLIRBuildBeforeRunTaskProvider : BeforeRunTaskProvider<MLIRBuildBeforeRunTaskProvider.Task>() {
    class Task : BeforeRunTask<Task>(ID) {
        init { isEnabled = true }
    }

    override fun getId() = ID
    override fun getName() = "Build MLIR test tools"
    override fun isConfigurable() = false
    override fun createTask(runConfiguration: RunConfiguration): Task? =
        if (runConfiguration is MLIRRunConfiguration) Task() else null

    override fun executeTask(context: DataContext, configuration: RunConfiguration,
                             environment: ExecutionEnvironment, task: Task): Boolean {
        val peer = (configuration as? WrappingRunConfiguration<*>)?.peer ?: configuration
        if (peer !is MLIRRunConfiguration) return false
        return try {
            val state = environment.getUserData(MLIRBuildPlanOwner.KEY) ?: environment.state as? MLIRBuildPlanOwner
                ?: throw ExecutionException("Cannot prepare MLIR tests.")
            val provider = CMakeBuildBeforeRunTaskProvider()
            for (tool in state.plan.buildConfigurations) {
                ProgressManager.checkCanceled()
                val buildTask = provider.createTask(tool)
                    ?: throw ExecutionException("Cannot create the CMake build task for '${tool.name}'.")
                if (!provider.executeTask(context, tool, environment, buildTask)) return false
            }
            true
        } catch (e: ExecutionException) {
            ExecutionUtil.handleExecutionError(environment, e)
            false
        }
    }

    companion object {
        val ID: Key<Task> = Key.create("MLIR.BuildTestTools")
    }
}
