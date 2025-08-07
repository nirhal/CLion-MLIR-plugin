package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import org.komlir.intellijmlirplugin.run_configuration.MLIRRunConfiguration
import java.awt.GridLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class MLIRRunConfigurationEditor : SettingsEditor<MLIRRunConfiguration>() {
    private val fileField = TextFieldWithBrowseButton()
    private val showAllProcessesOutputField = JCheckBox("Show all processes outputs")
    private val panel = JPanel(GridLayout(3, 1))

    init {
        panel.add(JLabel("File:"))
        panel.add(fileField)
        panel.add(showAllProcessesOutputField)
    }

    override fun resetEditorFrom(s: MLIRRunConfiguration) {
        fileField.text = s.file ?: ""
        showAllProcessesOutputField.isSelected = s.showAllProcessesOutput
    }

    override fun applyEditorTo(s: MLIRRunConfiguration) {
        s.file = fileField.text
        s.showAllProcessesOutput = showAllProcessesOutputField.isSelected
    }

    override fun createEditor(): JComponent {
        fileField.addBrowseFolderListener(
            null,
            FileChooserDescriptor(true, false, false, false, false, false)
                .withTitle("Select MLIR File"),
        )
        return panel
    }
}