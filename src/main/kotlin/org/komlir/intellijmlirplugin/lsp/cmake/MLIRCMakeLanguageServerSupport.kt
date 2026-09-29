package org.komlir.intellijmlirplugin.lsp.cmake

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.cpp.cmake.model.CMakeConfiguration
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspaceListener
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration
import com.jetbrains.cidr.cpp.execution.build.CMakeBuild
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment
import org.komlir.intellijmlirplugin.lsp.*
import org.komlir.intellijmlirplugin.run_configuration.MLIRCMakeTools
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class MLIRCMakeLanguageServerSupport : MLIRLanguageServerCMakeSupport {
    override fun targets(project: Project) = CMakeWorkspace.getInstance(project).modelTargets.mapNotNull { target ->
        val profiles = target.buildConfigurations.filter {
            it.targetType == CMakeConfiguration.TargetType.EXECUTABLE && it.productFile != null
        }.map { it.name }.distinct()
        profiles.takeIf { it.isNotEmpty() }?.let { MLIRLanguageServerCMakeSupport.Target(target.name, it) }
    }.sortedBy { it.name }

    override fun prepare(project: Project, options: MLIRLanguageServerSettings.Options, build: Boolean, indicator: ProgressIndicator): com.intellij.execution.configurations.GeneralCommandLine {
        if (options.target.isBlank() || options.profile.isBlank()) throw ExecutionException("Select an LSP CMake target and profile.")
        val workspace = CMakeWorkspace.getInstance(project)
        fun resolve() = MLIRCMakeTools(workspace.modelTargets, options.profile).resolve(options.target, true)!!
        var tool = resolve()
        fun environment() = workspace.getProfileInfoFor(tool.configuration).getEnvironmentSafe(true).also {
            if (it.hostMachine.isRemote || it.hostMachine.isWsl || it.hostMachine.hasRemoteFS()) {
                throw ExecutionException("MLIR language servers currently require a local CMake toolchain.")
            }
        }
        environment() // Reject unsupported toolchains before starting a build.
        indicator.checkCanceled()
        if (build) {
            indicator.text = "Building ${options.target} (${options.profile})"
            val processes = CopyOnWriteArrayList<ProcessHandler>()
            val cancelled = AtomicBoolean(false)
            val listener = object : ProcessAdapter() {
                override fun startNotified(event: ProcessEvent) {
                    processes += event.processHandler
                    if (cancelled.get()) event.processHandler.destroyProcess()
                }
            }
            val result = CMakeBuild.build(project, CMakeAppRunConfiguration.BuildAndRunConfigurations(tool.configuration), listener)
            try {
                while (true) {
                    indicator.checkCanceled()
                    val outcome = try { result.get(100, TimeUnit.MILLISECONDS) } catch (_: TimeoutException) { continue }
                    if (outcome.canceled) throw ProcessCanceledException()
                    if (!outcome.succeeded) throw ExecutionException("Building '${options.target}' failed. See the Build window.")
                    break
                }
            } catch (e: ProcessCanceledException) {
                cancelled.set(true)
                processes.forEach { it.destroyProcess() }
                throw e
            }
            tool = resolve()
        }
        indicator.checkCanceled()
        return MLIRLanguageServerCommand.create(options, project.basePath, tool.configuration.productFile!!.path).also {
            environment().prepare(it, CidrToolEnvironment.PrepareFor.RUN)
            // Explicit project settings take precedence over toolchain defaults.
            it.withEnvironment(options.environment)
        }
    }

    override fun subscribe(project: Project, parent: Disposable, changed: () -> Unit) {
        project.messageBus.connect(parent).subscribe(CMakeWorkspaceListener.TOPIC, object : CMakeWorkspaceListener {
            override fun reloadingFinished(canceled: Boolean) { if (!canceled) changed() }
        })
    }
}
