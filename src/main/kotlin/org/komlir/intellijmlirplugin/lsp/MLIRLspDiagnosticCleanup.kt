package org.komlir.intellijmlirplugin.lsp

import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import org.eclipse.lsp4j.PublishDiagnosticsParams

/** Tracks one client's diagnostics and clears them through the native notification pipeline. */
internal class MLIRLspDiagnosticCleanup(
    private val delegate: LspServerNotificationsHandler,
) : LspServerNotificationsHandler by delegate {
    private val uris = mutableSetOf<String>()
    private var stopped = false

    @Synchronized
    override fun publishDiagnostics(params: PublishDiagnosticsParams) {
        // A notification already in flight must not restore diagnostics after shutdown.
        if (stopped) return
        uris.add(params.uri)
        delegate.publishDiagnostics(params)
    }

    @Synchronized
    fun clear() {
        if (stopped) return
        stopped = true
        // Omit the version: cleanup also applies after unsaved edits or document closure.
        uris.forEach { delegate.publishDiagnostics(PublishDiagnosticsParams(it, emptyList())) }
        uris.clear()
    }
}
