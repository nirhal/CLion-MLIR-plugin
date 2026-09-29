package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import java.awt.GridLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class MLIRRunConfigurationEditor : SettingsEditor<MLIRRunConfiguration>() {
    private val fileField = TextFieldWithBrowseButton()
    private val showAllProcessesOutputField = JCheckBox("Show all processes outputs")
    private val recursiveField = JCheckBox("Include subfolders", true)
    private val panel = JPanel(GridLayout(4, 1))

    init {
        panel.add(JLabel("MLIR file or folder:"))
        panel.add(fileField)
        panel.add(recursiveField)
        panel.add(showAllProcessesOutputField)
    }

    override fun resetEditorFrom(s: MLIRRunConfiguration) {
        fileField.text = s.file ?: ""
        recursiveField.isSelected = s.recursive
        showAllProcessesOutputField.isSelected = s.showAllProcessesOutput
    }

    override fun applyEditorTo(s: MLIRRunConfiguration) {
        s.file = fileField.text
        s.recursive = recursiveField.isSelected
        s.showAllProcessesOutput = showAllProcessesOutputField.isSelected
        s.updateSettings()
    }

    override fun createEditor(): JComponent {
        fileField.addBrowseFolderListener(
            null,
            FileChooserDescriptor(true, true, false, false, false, false)
                .withTitle("Select MLIR File or Folder"),
        )
        return panel
    }
}