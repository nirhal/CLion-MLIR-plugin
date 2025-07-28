package org.komlir.intellijmlirplugin.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import org.komlir.intellijmlirplugin.MLIRFileType
import org.komlir.intellijmlirplugin.MLIRLanguage

class MLIRFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, MLIRLanguage) {
    override fun getFileType(): FileType = MLIRFileType

    override fun toString(): String = "MLIR File"
}