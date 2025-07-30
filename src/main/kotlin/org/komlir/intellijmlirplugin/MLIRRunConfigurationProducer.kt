package org.komlir.intellijmlirplugin

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.jetbrains.cidr.cpp.cmake.model.CMakeConfiguration
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import org.komlir.intellijmlirplugin.run_configuration.MLIRRunConfiguration
import org.komlir.intellijmlirplugin.run_configuration.MLIRRunConfigurationType

class MLIRRunConfigurationProducer : LazyRunConfigurationProducer<MLIRRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        return MLIRRunConfigurationType().factory
    }

    override fun setupConfigurationFromContext(
        configuration: MLIRRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement?>
    ): Boolean {
        val element = context.psiLocation ?: return false
        if (element.containingFile == null) return false
        val file = element.containingFile.virtualFile

        val commandTemplate = RunCommandParser.searchCommand(element) ?: return false
        val conf = findOptConfigurations(element.project, commandTemplate)?.firstOrNull() ?: return false
        val command = commandTemplate.replace("%s", file.name)
        val optDir = conf.buildWorkingDir

        configuration.name = file.name
        configuration.command = "${optDir.canonicalPath}/${command}"
        configuration.workingDir = file.parent.canonicalPath
        return true
    }

    override fun isConfigurationFromContext(
        configuration: MLIRRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        return false
    }

    private fun getOptConfigurations(project: Project, optToolName: String): List<CMakeConfiguration> {
        val cmakeWorkspace = CMakeWorkspace.getInstance(project)
        return cmakeWorkspace.modelTargets.find { it.name == optToolName }?.buildConfigurations ?: emptyList()
    }

    private fun findOptConfigurations(project: Project, command: String): List<CMakeConfiguration>? {
        val optToolName = command.split(" ").firstOrNull() ?: return null
        return getOptConfigurations(project, optToolName)
    }

}