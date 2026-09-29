package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.jetbrains.cidr.cpp.cmake.model.CMakeTarget
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import com.jetbrains.cidr.cpp.execution.CMakeBuildProfileExecutionTarget
import java.io.File

/** Immutable selection shared by the build step and the test runner. */
internal data class MLIRExecutionPlan(
    val tests: List<MLIRTestDiscovery.TestCase>,
    val suiteName: String?,
    val launchers: Map<Pair<String, RunCommandParser.Pipeline>, MLIRCMakeLauncher>,
    val buildConfigurations: List<MLIRRunConfiguration>,
) {
    companion object {
        fun prepare(configuration: MLIRRunConfiguration, environment: ExecutionEnvironment,
                    targets: List<CMakeTarget> = CMakeWorkspace.getInstance(configuration.project).modelTargets): MLIRExecutionPlan {
            val discovered = MLIRTestDiscovery.discover(configuration.project, configuration.file!!,
                configuration.recursive, configuration.selectedTestPaths)
            if (discovered.isEmpty()) throw ExecutionException("No .mlir files with RUN directives found in ${configuration.file}.")
            val profile = (environment.executionTarget as? CMakeBuildProfileExecutionTarget)?.profileName
                ?: configuration.targetAndConfigurationData?.configurationName
            val tools = MLIRCMakeTools(targets, profile)
            val builds = linkedMapOf<String, MLIRRunConfiguration>()
            val launchers = linkedMapOf<Pair<String, RunCommandParser.Pipeline>, MLIRCMakeLauncher>()
            val tests = discovered.map { test ->
                try {
                    for (pipeline in test.test?.pipelines.orEmpty()) {
                        ProgressManager.checkCanceled()
                        val first = pipeline.commands.first()
                        val snapshot = configuration.commandConfiguration(test.path, first, tools.resolve(first.executable, true)!!)
                        builds.putIfAbsent(pipeline.commands.first().executable, snapshot)
                        // Known downstream CMake tools are built too; other tools retain PATH lookup.
                        val commands = pipeline.commands.mapIndexed { index, command ->
                            val tool = if (index == 0) null else tools.resolve(command.executable, false)
                            if (tool == null) command else {
                                builds.putIfAbsent(command.executable, configuration.commandConfiguration(test.path, command, tool))
                                command.copy(executable = tool.configuration.productFile!!.path)
                            }
                        }
                        launchers[test.path to pipeline] = MLIRCMakeLauncher(environment, snapshot, pipeline.copy(commands = commands))
                    }
                    test
                } catch (e: IllegalStateException) {
                    test.copy(test = null, error = e.message)
                }
            }
            return MLIRExecutionPlan(tests, if (configuration.isFolder) File(configuration.file!!).name else null,
                launchers, builds.values.toList())
        }
    }
}

internal interface MLIRBuildPlanOwner {
    val plan: MLIRExecutionPlan

    companion object {
        // ExecutionEnvironmentBuilder copies user data into before-run task environments.
        val KEY: Key<MLIRBuildPlanOwner> = Key.create("MLIR.preparedRunState")
    }
}
