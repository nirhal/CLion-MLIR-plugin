package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.openapi.options.SettingsEditor
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class MLIRRunConfigurationEditor : SettingsEditor<MLIRRunConfiguration>() {
    private val commandField = JTextField()
    private val dirField = JTextField()
    private val panel = JPanel(GridLayout(2, 2))

    init {
        panel.add(JLabel("Working Directory:"))
        panel.add(dirField)
        panel.add(JLabel("Command:"))
        panel.add(commandField)
    }

    override fun resetEditorFrom(s: MLIRRunConfiguration) {
        commandField.text = s.command
        dirField.text = s.workingDir
    }

    override fun applyEditorTo(s: MLIRRunConfiguration) {
        s.command = commandField.text
        s.workingDir = dirField.text
    }

    override fun createEditor(): JComponent {
        return panel
    }
}