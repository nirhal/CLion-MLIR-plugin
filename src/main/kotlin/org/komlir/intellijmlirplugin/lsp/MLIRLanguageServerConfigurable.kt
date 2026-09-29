package org.komlir.intellijmlirplugin.lsp

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.util.ui.FormBuilder
import javax.swing.*

class MLIRLanguageServerConfigurable(private val project: Project) : SearchableConfigurable {
    private var panel: JPanel? = null
    private val enabled = JCheckBox("Enable MLIR language server")
    private val source = ComboBox(arrayOf("CMake target", "Executable path"))
    private val executable = TextFieldWithBrowseButton()
    private val target = ComboBox<String>().apply { isEditable = true }
    private val profile = ComboBox<String>().apply { isEditable = true }
    private val executableLabel = JLabel("Executable:").apply { labelFor = executable }
    private val targetLabel = JLabel("CMake target:").apply { labelFor = target }
    private val profileLabel = JLabel("CMake profile:").apply { labelFor = profile }
    private val arguments = JTextField()
    private val directory = TextFieldWithBrowseButton()
    private val environment = EnvironmentVariablesComponent()
    private val build = JCheckBox("Build target before starting the server")
    private val refresh = JButton("Refresh targets")
    private val rebuild = JButton("Build and restart")
    private val status = JLabel()
    private var timer: Timer? = null
    private var targets = emptyList<MLIRLanguageServerCMakeSupport.Target>()

    override fun getId() = "mlir.languageServer"
    override fun getDisplayName() = "MLIR Language Server"

    override fun createComponent(): JComponent {
        panel?.let { return it }
        executable.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withTitle("Select MLIR Language Server"))
        directory.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Language Server Working Directory"))
        refresh.addActionListener { refreshTargets() }
        source.addActionListener { updateControls() }
        target.addActionListener { refreshProfiles() }
        val restart = JButton("Restart server").apply { addActionListener { applyAndRestart(false) } }
        rebuild.addActionListener { applyAndRestart(true) }
        val actions = JPanel().apply { add(restart); add(rebuild); add(refresh) }
        panel = FormBuilder.createFormBuilder()
            .addComponent(enabled)
            .addLabeledComponent("Server source:", source)
            .addLabeledComponent(executableLabel, executable)
            .addLabeledComponent(targetLabel, target)
            .addLabeledComponent(profileLabel, profile)
            .addLabeledComponent("Arguments:", arguments)
            .addLabeledComponent("Working directory:", directory)
            .addComponent(environment)
            .addComponent(build)
            .addComponent(JLabel("Paths may be relative to the project. Working directory defaults to the project directory."))
            .addComponent(JLabel("Use an LSP server that registers your project's MLIR dialects. Local toolchains only."))
            .addComponent(actions)
            .addLabeledComponent("Status:", status)
            .addComponentFillVertically(JPanel(), 0).panel
        refreshTargets()
        reset()
        timer = Timer(1000) { updateStatus() }.also { it.start() }
        updateStatus()
        return panel!!
    }

    private fun refreshTargets() {
        val selected = target.editor.item?.toString().orEmpty()
        targets = MLIRLanguageServerCMakeSupport.get()?.targets(project).orEmpty()
        target.model = DefaultComboBoxModel(targets.map { it.name }.distinct().toTypedArray())
        target.selectedItem = selected
        refreshProfiles()
    }

    private fun refreshProfiles() {
        val selected = profile.editor.item?.toString().orEmpty()
        profile.model = DefaultComboBoxModel(targets.firstOrNull { it.name == target.editor.item?.toString() }
            ?.profiles.orEmpty().toTypedArray())
        if (selected.isNotBlank()) profile.selectedItem = selected
    }

    private fun updateControls() {
        val cmake = source.selectedIndex == 0
        listOf(executableLabel, executable).forEach { it.isVisible = !cmake }
        listOf(targetLabel, target, profileLabel, profile, build, refresh, rebuild).forEach { it.isVisible = cmake }
        panel?.revalidate()
        panel?.repaint()
    }

    private fun options() = MLIRLanguageServerSettings.Options(
        enabled = enabled.isSelected,
        source = if (source.selectedIndex == 0) MLIRLanguageServerSettings.Source.CMAKE else MLIRLanguageServerSettings.Source.EXECUTABLE,
        executable = executable.text.trim(), target = target.editor.item?.toString()?.trim().orEmpty(),
        profile = profile.editor.item?.toString()?.trim().orEmpty(), arguments = arguments.text,
        workingDirectory = directory.text.trim(), environment = LinkedHashMap(environment.envs), buildBeforeStart = build.isSelected,
        passParentEnvironment = environment.isPassParentEnvs,
    )

    override fun isModified() = panel != null && options() != project.service<MLIRLanguageServerSettings>().state

    override fun apply() = save(restartIfChanged = true)

    private fun save(restartIfChanged: Boolean) {
        val options = options()
        if (options.enabled) {
            try {
                if (options.source == MLIRLanguageServerSettings.Source.EXECUTABLE) {
                    MLIRLanguageServerCommand.create(options, project.basePath)
                } else {
                    val support = MLIRLanguageServerCMakeSupport.get() ?: error("CMake targets require CLion's CMake support.")
                    val candidate = support.targets(project).firstOrNull { it.name == options.target }
                        ?: error("CMake target '${options.target}' is unavailable. Reload CMake or select an executable target.")
                    check(options.profile in candidate.profiles) { "Select an available CMake profile for '${options.target}'." }
                }
            } catch (e: Exception) { throw ConfigurationException(e.message ?: "Invalid language server settings") }
        }
        val modified = options != project.service<MLIRLanguageServerSettings>().state
        project.service<MLIRLanguageServerSettings>().loadState(options)
        if (modified && restartIfChanged) project.service<MLIRLanguageServerService>().restart()
    }

    private fun applyAndRestart(build: Boolean) {
        try {
            save(restartIfChanged = false)
            project.service<MLIRLanguageServerService>().restart(build)
        } catch (e: ConfigurationException) {
            com.intellij.openapi.ui.Messages.showErrorDialog(project, e.message, "MLIR Language Server")
        }
    }

    override fun reset() {
        val options = project.service<MLIRLanguageServerSettings>().state
        enabled.isSelected = options.enabled
        source.selectedIndex = if (options.source == MLIRLanguageServerSettings.Source.CMAKE) 0 else 1
        executable.text = options.executable
        target.selectedItem = options.target
        profile.selectedItem = options.profile
        arguments.text = options.arguments
        directory.text = options.workingDirectory
        environment.envs = LinkedHashMap(options.environment)
        environment.isPassParentEnvs = options.passParentEnvironment
        build.isSelected = options.buildBeforeStart
        updateControls()
    }

    private fun updateStatus() {
        val settings = project.service<MLIRLanguageServerSettings>().state
        val servers = LspServerManager.getInstance(project).getServersForProvider(MLIRLspServerSupportProvider::class.java)
        status.text = if (!settings.enabled) "Disabled" else servers.firstOrNull()?.state?.toString()
            ?: project.service<MLIRLanguageServerService>().preparationStatus
    }

    override fun disposeUIResources() {
        timer?.stop()
        timer = null
        panel = null
    }
}
