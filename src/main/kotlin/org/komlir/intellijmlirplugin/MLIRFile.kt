package org.komlir.intellijmlirplugin

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class MLIRFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, MLIRLanguage) {
    override fun getFileType(): FileType = MLIRFileType
    
    override fun toString(): String = "MLIR File"
}
