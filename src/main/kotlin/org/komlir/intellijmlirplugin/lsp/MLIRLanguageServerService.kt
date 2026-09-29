package org.komlir.intellijmlirplugin.lsp

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerSupportProvider

/** Preparation happens outside the LSP start callback and never blocks the EDT. */
@Service(Service.Level.PROJECT)
class MLIRLanguageServerService(private val project: Project) : Disposable {
    private val lock = Any()
    @Volatile private var generation = 0L
    private var preparing = false
    private var failed = false
    private var forceBuild = false
    private var descriptor: MLIRLspServerDescriptor? = null
    private var preparation: ProgressIndicator? = null
    @Volatile private var disposed = false
    @Volatile var preparationStatus = "Not started"
        private set

    init {
        MLIRLspDiagnosticCleanup(project, this)
        MLIRLanguageServerCMakeSupport.get()?.subscribe(project, this) {
            if (project.service<MLIRLanguageServerSettings>().state.source == MLIRLanguageServerSettings.Source.CMAKE) restart()
        }
    }

    fun fileOpened(starter: LspServerSupportProvider.LspServerStarter) {
        val request = synchronized(lock) {
            if (disposed || !project.service<MLIRLanguageServerSettings>().state.enabled) return
            descriptor?.let { starter.ensureServerStarted(it); return }
            if (preparing || failed) return
            preparing = true
            val ticket = generation
            val options = project.service<MLIRLanguageServerSettings>().state.snapshot()
            val build = forceBuild || options.buildBeforeStart
            forceBuild = false
            preparationStatus = if (build && options.source == MLIRLanguageServerSettings.Source.CMAKE) "Building" else "Preparing"
            Triple(ticket, options, build)
        }
        val (ticket, options, build) = request
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Preparing MLIR language server", true) {
            override fun run(indicator: ProgressIndicator) {
                synchronized(lock) {
                    if (!isCurrent(ticket)) return
                    preparation = indicator
                }
                try {
                    val command = when (options.source) {
                        MLIRLanguageServerSettings.Source.EXECUTABLE -> MLIRLanguageServerCommand.create(options, project.basePath)
                        MLIRLanguageServerSettings.Source.CMAKE -> (MLIRLanguageServerCMakeSupport.get()
                            ?: error("CMake language server targets require CLion's CMake support."))
                            .prepareWithContext(project, options, build, indicator)
                    }
                    indicator.checkCanceled()
                    synchronized(lock) {
                        if (!isCurrent(ticket)) return
                        descriptor = MLIRLspServerDescriptor(project, command)
                        preparationStatus = "Ready"
                    }
                    onEdt {
                        synchronized(lock) {
                            if (isCurrent(ticket)) manager().startServersIfNeeded(MLIRLspServerSupportProvider::class.java)
                        }
                    }
                } catch (e: ProcessCanceledException) {
                    synchronized(lock) {
                        if (isCurrent(ticket)) { failed = true; preparationStatus = "Cancelled — restart to try again" }
                    }
                } catch (e: Exception) {
                    synchronized(lock) {
                        if (!isCurrent(ticket)) return
                        failed = true
                        preparationStatus = "Failed: ${e.message ?: e.javaClass.simpleName}"
                    }
                    Logger.getInstance(javaClass).warn("Cannot prepare MLIR language server", e)
                    onEdt {
                        if (isCurrent(ticket)) NotificationGroupManager.getInstance().getNotificationGroup("MLIR Language Server")
                            .createNotification("Cannot start MLIR language server", e.message ?: "Unknown error", NotificationType.ERROR)
                            .notify(project)
                    }
                } finally {
                    synchronized(lock) { if (isCurrent(ticket)) { preparing = false; preparation = null } }
                }
            }
        })
    }

    fun restart(build: Boolean = false) = onEdt {
        synchronized(lock) {
            generation++
            preparation?.cancel()
            preparation = null
            preparing = false
            failed = false
            descriptor = null
            forceBuild = build
            preparationStatus = if (project.service<MLIRLanguageServerSettings>().state.enabled) "Not started" else "Disabled"
        }
        manager().stopAndRestartIfNeeded(MLIRLspServerSupportProvider::class.java)
    }

    private fun isCurrent(ticket: Long) = !disposed && !project.isDisposed && generation == ticket
    private fun manager() = LspServerManager.getInstance(project)
    private fun onEdt(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater({ if (!disposed && !project.isDisposed) action() }, project.disposed)
    }

    override fun dispose() {
        synchronized(lock) {
            disposed = true
            generation++
            preparation?.cancel()
            descriptor = null
        }
        // LspServerManager owns and disposes running server processes with the project.
    }
}
