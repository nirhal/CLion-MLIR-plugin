package org.komlir.intellijmlirplugin.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import org.komlir.intellijmlirplugin.MLIRLanguage

object MLIRElementFactory {

    fun createSSAValue(project: Project, name: String): PsiElement {
        return createFile(project, "%$name = dummy_op").firstChild as MLIRSSAValueElement
    }

    fun createSymbol(project: Project, name: String): PsiElement {
        return createFile(project, "@$name").firstChild as MLIRSymbolElement
    }

    private fun createFile(
        project: Project,
        dummyText: String
    ): MLIRFile = PsiFileFactory.getInstance(project)
        .createFileFromText("dummy.mlir", MLIRLanguage, dummyText) as MLIRFile
}