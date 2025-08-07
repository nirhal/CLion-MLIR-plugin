package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
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
import com.jetbrains.cidr.execution.ExecutableData
import d.L.g.v
import org.jdom.Element

class MLIRRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
): CMakeAppRunConfiguration(project, factory, name) {

    private var myFile: String? = null

    var file: String?
        get() = myFile
        set(value) {
            myFile = value
            updateSettings()
        }
    var showAllProcessesOutput: Boolean = false

    override fun writeExternal(element: Element) {
        element.setAttribute("MLIR_FILE_PATH", file ?: "")
        element.setAttribute("SHOW_ALL_PROCESSES_OUTPUT", showAllProcessesOutput.toString())
//        updateSettings()
        super.writeExternal(element)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        file = element.getAttributeValue("MLIR_FILE_PATH")
        showAllProcessesOutput = element.getAttributeValue("SHOW_ALL_PROCESSES_OUTPUT")?.toBoolean() ?: false
//        updateSettings()
    }

    override fun clone(): RunConfiguration {
        val clone = super.clone() as MLIRRunConfiguration
        clone.file = this.file
        clone.showAllProcessesOutput = this.showAllProcessesOutput
        clone.updateSettings()
        return clone
    }

    var pipeCommands : List<RunCommandParser.Command> = listOf()

    private fun updateSettings(): Boolean {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(file ?: return false) ?: return false
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return false
        val parseResult =
            RunCommandParser.searchAndParseCommand(psiFile, virtualFile.canonicalPath ?: return false) ?: return false
        val target = getCMakeTarget(project, parseResult.mainCommand.executable)
        val conf = target?.buildConfigurations?.firstOrNull() ?: return false

        val buildAndTargetConf = BuildTargetAndConfigurationData(target, conf)
        targetAndConfigurationData = buildAndTargetConf
        executableData = buildAndTargetConf.target?.let { ExecutableData(it) }
        workingDirectory = virtualFile.parent.canonicalPath
        programParameters = parseResult.mainCommand.arguments
        pipeCommands = parseResult.pipedCommands
        return true
    }

    private fun getCMakeTarget(project: Project, optToolName: String): CMakeTarget? {
        val cmakeWorkspace = CMakeWorkspace.Companion.getInstance(project)
        return cmakeWorkspace.modelTargets.find { it.name == optToolName }
    }

    override fun getType(): CMakeRunConfigurationType {
        return super.getType() as MLIRRunConfigurationType
    }

    override fun getState(executor: Executor, env: ExecutionEnvironment): CommandLineState? {
        return CidrCommandLineState(env, MLIRCMakeLauncher(env, this))
    }

}