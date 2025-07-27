package org.komlir.intellijmlirplugin

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object MLIRFileType : LanguageFileType(MLIRLanguage) {
    override fun getName(): String = "MLIR"
    override fun getDescription(): String = "MLIR (Multi-Level Intermediate Representation) file"
    override fun getDefaultExtension(): String = "mlir"
    override fun getIcon(): Icon? = null // You can add an icon later
}
