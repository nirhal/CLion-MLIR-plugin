package org.komlir.intellijmlirplugin

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.komlir.intellijmlirplugin.psi.MLIRSSAValueReference


/**
 * Base PSI element implementation for MLIR
 */
open class MLIRElementImpl(node: ASTNode) : ASTWrapperPsiElement(node)

/**
 * PSI element specifically for SSA values that provides references
 */
class MLIRSSAValueElement(node: ASTNode) : MLIRElementImpl(node), PsiNamedElement {
    
    override fun getReference(): PsiReference? {
        return MLIRSSAValueReference(this)
    }

    override fun getName(): String? {
        return text?.removePrefix("%")
    }

    override fun setName(name: String): PsiElement? {
        return replace(MLIRElementFactory.createSSAValue(project, name))
    }
}

object MLIRElementFactory {


    fun createSSAValue(project: Project, name: String): PsiElement {
        val dummyText = "%$name = dummy_op"
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("dummy.mlir", MLIRLanguage, dummyText) as MLIRFile

        val op = file.firstChild as MLIRSSAValueElement
        return op
    }
}
