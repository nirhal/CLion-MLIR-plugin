// CLion 2026.1 exposes no public API for refreshing the native LSP markup after shutdown.
// Keep the compatibility workaround isolated here.
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package org.komlir.intellijmlirplugin.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerManagerListener
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.impl.highlighting.LspHighlightingApplier
import java.util.concurrent.ConcurrentHashMap

/** Refresh native LSP markup after shutdown, when the stopped server has been removed. */
internal class MLIRLspDiagnosticCleanup(private val project: Project, parent: Disposable) : LspServerManagerListener {
    private val files = ConcurrentHashMap<LspServer, MutableSet<VirtualFile>>()

    init {
        LspServerManager.getInstance(project).addLspServerManagerListener(this, parent)
    }

    override fun diagnosticsReceived(lspServer: LspServer, file: VirtualFile) {
        if (lspServer.providerClass == MLIRLspServerSupportProvider::class.java) {
            files.computeIfAbsent(lspServer) { ConcurrentHashMap.newKeySet() }.add(file)
        }
    }

    override fun serverStateChanged(lspServer: LspServer) {
        if (lspServer.state != LspServerState.ShutdownNormally &&
            lspServer.state != LspServerState.ShutdownUnexpectedly) return
        val affected = files.remove(lspServer) ?: return
        ApplicationManager.getApplication().invokeLater({
            if (!project.isDisposed) {
                // CLion 2026.1's ordinary daemon pass skips files with no LSP server.
                // Use its native refresh to replace only LSP markup, retaining other
                // inspections and any results from a newly started server.
                val applier = LspHighlightingApplier.getInstance(project)
                affected.filter { it.isValid }.forEach { applier.scheduleHighlightingRefresh(it) }
            }
        }, project.disposed)
    }
}
