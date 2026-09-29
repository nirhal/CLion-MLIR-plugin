package org.komlir.intellijmlirplugin.lsp

import com.intellij.openapi.components.*

/** Machine-specific executable paths and environment belong in workspace.xml. */
@Service(Service.Level.PROJECT)
@State(name = "MLIRLanguageServer", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class MLIRLanguageServerSettings : PersistentStateComponent<MLIRLanguageServerSettings.Options> {
    enum class Source { EXECUTABLE, CMAKE }

    data class Options(
        var enabled: Boolean = false,
        var source: Source = Source.EXECUTABLE,
        var executable: String = "",
        var target: String = "",
        var profile: String = "",
        var arguments: String = "",
        var workingDirectory: String = "",
        var environment: MutableMap<String, String> = linkedMapOf(),
        var passParentEnvironment: Boolean = true,
        var buildBeforeStart: Boolean = true,
    ) {
        fun snapshot() = copy(environment = LinkedHashMap(environment))
    }

    private var options = Options()
    override fun getState() = options
    override fun loadState(state: Options) { options = state.snapshot() }
}
