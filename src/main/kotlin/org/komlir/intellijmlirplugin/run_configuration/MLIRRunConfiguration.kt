package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.Executor
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration
import com.jetbrains.cidr.cpp.execution.CMakeRunConfigurationType
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import com.jetbrains.cidr.execution.BuildTargetAndConfigurationData
import com.jetbrains.cidr.execution.CidrCommandLineState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.ui.ConsoleView
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.cidr.execution.ExecutableData
import org.jdom.Element
import com.intellij.execution.BeforeRunTask
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.ThrowableComputable
import com.jetbrains.cidr.execution.CidrBuildBeforeRunTaskProvider

class MLIRRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
): CMakeAppRunConfiguration(project, factory, name) {

    var file: String? = null
    var showAllProcessesOutput: Boolean = false
    var recursive: Boolean = true
    // Only set on a rerun snapshot; never persisted into the user's configuration.
    internal var selectedTestPaths: Set<String>? = null
    val isFolder: Boolean get() = file?.let { LocalFileSystem.getInstance().findFileByPath(it)?.isDirectory } == true

    override fun writeExternal(element: Element) {
        element.setAttribute("MLIR_FILE_PATH", file ?: "")
        element.setAttribute("MLIR_RECURSIVE", recursive.toString())
        element.setAttribute("SHOW_ALL_PROCESSES_OUTPUT", showAllProcessesOutput.toString())
        super.writeExternal(element)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        file = element.getAttributeValue("MLIR_FILE_PATH")
        recursive = element.getAttributeValue("MLIR_RECURSIVE")?.toBoolean() ?: true
        showAllProcessesOutput = element.getAttributeValue("SHOW_ALL_PROCESSES_OUTPUT")?.toBoolean() ?: false
    }

    override fun clone(): RunConfiguration {
        val clone = super.clone() as MLIRRunConfiguration
        clone.file = this.file
        clone.showAllProcessesOutput = this.showAllProcessesOutput
        clone.recursive = recursive
        clone.selectedTestPaths = selectedTestPaths?.toSet()
        return clone
    }

    var testFile: RunCommandParser.TestFile? = null
        private set
    var validationError: String? = null
        private set

    fun updateSettings(): Boolean {
        validationError = null
        val selected = file?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        validationError = when {
            file.isNullOrBlank() -> "Select an MLIR file or folder."
            selected == null -> "MLIR file or folder does not exist: $file"
            !selected.isDirectory && selected.extension != "mlir" -> "Select a .mlir file or a folder."
            else -> null
        }
        return validationError == null
    }

    internal fun commandConfiguration(path: String, command: RunCommandParser.Command,
                                      tool: MLIRCMakeTools.Tool): MLIRRunConfiguration {
        val snapshot = clone() as MLIRRunConfiguration
        snapshot.file = path
        snapshot.workingDirectory = java.io.File(path).parent
        val data = BuildTargetAndConfigurationData(tool.target, tool.configuration)
        snapshot.targetAndConfigurationData = data
        snapshot.executableData = data.target?.let { ExecutableData(it) }
        snapshot.programParameters = ParametersListUtil.join(command.arguments)
        snapshot.isEmulateTerminal = false
        snapshot.isUseExternalConsole = false
        return snapshot
    }

    override fun getType(): CMakeRunConfigurationType {
        return super.getType() as MLIRRunConfigurationType
    }

    override fun getBuildProfiles(): List<String> = CMakeWorkspace.getInstance(project).modelTargets
        .flatMap { it.buildConfigurations }.map { it.name }.distinct()

    override fun checkConfiguration() {
        // Folder traversal and CMake resolution happen in the cancellable preparation step.
        if (!updateSettings()) throw RuntimeConfigurationError(validationError ?: "Cannot configure MLIR tests.")
    }

    override fun checkSettingsBeforeRun() = checkConfiguration()

    override fun getBeforeRunTasks(): List<BeforeRunTask<*>> {
        val tasks = super.getBeforeRunTasks().map { task ->
            if (task.providerId == CidrBuildBeforeRunTaskProvider.ID) {
                MLIRBuildBeforeRunTaskProvider.Task().also { it.isEnabled = task.isEnabled }
            } else task
        }
        val unique = tasks.filterIndexed { index, task ->
            task.providerId != MLIRBuildBeforeRunTaskProvider.ID ||
                tasks.take(index).none { it.providerId == MLIRBuildBeforeRunTaskProvider.ID }
        }
        return if (unique.any { it.providerId == MLIRBuildBeforeRunTaskProvider.ID }) unique
            else listOf(MLIRBuildBeforeRunTaskProvider.Task()) + unique
    }

    override fun getState(executor: Executor, env: ExecutionEnvironment): CommandLineState {
        if (!updateSettings()) throw ExecutionException(validationError ?: "Cannot configure MLIR tests.")
        if (executor.id == DefaultDebugExecutor.EXECUTOR_ID && isFolder) {
            throw ExecutionException("Folder debugging is not supported. Select an MLIR file to debug its first RUN directive.")
        }
        val snapshot = clone() as MLIRRunConfiguration
        snapshot.workingDirectory = if (isFolder) file else java.io.File(file!!).parent
        val prepared = lazy {
            val prepare = ThrowableComputable<MLIRExecutionPlan, ExecutionException> { MLIRExecutionPlan.prepare(snapshot, env) }
            if (ApplicationManager.getApplication().isDispatchThread) {
                ProgressManager.getInstance().runProcessWithProgressSynchronously(prepare, "Preparing MLIR tests", true, project)
            } else prepare.compute()
        }
        if (executor.id == DefaultDebugExecutor.EXECUTOR_ID) {
            val plan = prepared.value
            val test = plan.tests.single()
            val parsed = test.test ?: throw ExecutionException(test.error ?: "Invalid MLIR test.")
            val launcher = plan.launchers.getValue(test.path to parsed.pipelines.first())
            launcher.configuration.testFile = parsed
            return object : CidrCommandLineState(env, launcher), MLIRBuildPlanOwner {
                override val plan = prepared.value
            }.also { env.putUserData(MLIRBuildPlanOwner.KEY, it) }
        }
        return object : CommandLineState(env), MLIRBuildPlanOwner {
            override val plan: MLIRExecutionPlan get() = prepared.value
            private val properties by lazy { MLIRTestConsoleProperties(snapshot, executor, plan.tests) }

            override fun startProcess() = MLIRTestProcessHandler(plan.tests, plan.suiteName) { test, pipeline ->
                plan.launchers.getValue(test.path to pipeline).createProcess(this)
            }

            override fun createConsole(executor: Executor): ConsoleView = properties.createTestConsole(
                plan.launchers.values.firstOrNull()?.getRunFileAndEnvironment()?.second)

            override fun execute(executor: Executor, runner: ProgramRunner<*>): com.intellij.execution.ExecutionResult {
                val result = super.execute(executor, runner) as DefaultExecutionResult
                result.setRestartActions(properties.createRerunFailedTestsAction(result.executionConsole as ConsoleView))
                return result
            }
        }.also { env.putUserData(MLIRBuildPlanOwner.KEY, it) }
    }
}
