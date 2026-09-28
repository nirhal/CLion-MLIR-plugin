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
import com.intellij.psi.PsiManager
import com.jetbrains.cidr.cpp.cmake.model.CMakeTarget
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration
import com.jetbrains.cidr.cpp.execution.CMakeRunConfigurationType
import com.jetbrains.cidr.execution.BuildTargetAndConfigurationData
import com.jetbrains.cidr.execution.CidrCommandLineState
import com.jetbrains.cidr.execution.CidrRestartActionProvider
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.ui.ConsoleView
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.cidr.execution.ExecutableData
import org.jdom.Element

class MLIRRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
): CMakeAppRunConfiguration(project, factory, name) {

    var file: String? = null
    var showAllProcessesOutput: Boolean = false

    override fun writeExternal(element: Element) {
        element.setAttribute("MLIR_FILE_PATH", file ?: "")
        element.setAttribute("SHOW_ALL_PROCESSES_OUTPUT", showAllProcessesOutput.toString())
        super.writeExternal(element)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        file = element.getAttributeValue("MLIR_FILE_PATH")
        showAllProcessesOutput = element.getAttributeValue("SHOW_ALL_PROCESSES_OUTPUT")?.toBoolean() ?: false
    }

    override fun clone(): RunConfiguration {
        val clone = super.clone() as MLIRRunConfiguration
        clone.file = this.file
        clone.showAllProcessesOutput = this.showAllProcessesOutput
        return clone
    }

    var testFile: RunCommandParser.TestFile? = null
        private set
    var validationError: String? = null
        private set

    fun updateSettings(): Boolean {
        testFile = null
        validationError = null
        try {
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(file ?: error("Select an MLIR file."))
                ?: error("MLIR file does not exist: $file")
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: error("Cannot read MLIR file: $file")
            val parsed = RunCommandParser.parse(psiFile, virtualFile.path)
            workingDirectory = virtualFile.parent.path
            applyCommand(parsed.pipelines.first().commands.first())
            testFile = parsed
            return true
        } catch (e: IllegalArgumentException) {
            validationError = e.message
        } catch (e: IllegalStateException) {
            validationError = e.message
        }
        return false
    }

    private fun applyCommand(command: RunCommandParser.Command) {
        val target = getCMakeTarget(project, command.executable)
            ?: error("No CMake target named '${command.executable}'.")
        val selectedProfile = targetAndConfigurationData?.configurationName
        val conf = target.buildConfigurations.firstOrNull { it.name == selectedProfile }
            ?: target.buildConfigurations.firstOrNull() ?: error("No build configuration for '${command.executable}'.")
        val buildAndTargetConf = BuildTargetAndConfigurationData(target, conf)
        targetAndConfigurationData = buildAndTargetConf
        executableData = buildAndTargetConf.target?.let { ExecutableData(it) }
        programParameters = ParametersListUtil.join(command.arguments)
        isEmulateTerminal = false
        isUseExternalConsole = false
    }

    private fun getCMakeTarget(project: Project, optToolName: String): CMakeTarget? {
        val cmakeWorkspace = CMakeWorkspace.Companion.getInstance(project)
        return cmakeWorkspace.modelTargets.find { it.name == optToolName }
    }

    override fun getType(): CMakeRunConfigurationType {
        return super.getType() as MLIRRunConfigurationType
    }

    override fun checkConfiguration() {
        if (!updateSettings()) throw RuntimeConfigurationError(validationError ?: "Cannot configure MLIR test.")
        super.checkConfiguration()
    }

    override fun getState(executor: Executor, env: ExecutionEnvironment): CommandLineState {
        if (!updateSettings()) throw ExecutionException(validationError ?: "Cannot configure MLIR test.")
        val test = testFile!!
        val launchers = test.pipelines.associateWith { pipeline ->
            val snapshot = clone() as MLIRRunConfiguration
            try { snapshot.applyCommand(pipeline.commands.first()) }
            catch (e: IllegalStateException) { throw ExecutionException("RUN at line ${pipeline.line}: ${e.message}", e) }
            MLIRCMakeLauncher(env, snapshot, pipeline)
        }
        val firstLauncher = launchers.getValue(test.pipelines.first())
        if (executor.id == DefaultDebugExecutor.EXECUTOR_ID) return CidrCommandLineState(env, firstLauncher)
        val properties = MLIRTestConsoleProperties(this, executor)
        return object : CidrCommandLineState(env, firstLauncher), CidrRestartActionProvider {
            override fun startProcess() = MLIRTestProcessHandler(test) { pipeline ->
                launchers.getValue(pipeline).createProcess(this)
            }

            override fun createConsole(executor: Executor): ConsoleView =
                SMTestRunnerConnectionUtil.createConsole(properties)

            override fun createRestartAction(console: ExecutionConsole): AnAction =
                properties.createRerunFailedTestsAction(console as ConsoleView)
        }
    }

}
