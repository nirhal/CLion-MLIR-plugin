package org.komlir.intellijmlirplugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference


/**
 * Base PSI element implementation for MLIR
 */
sealed class MLIRElement(node: ASTNode) : ASTWrapperPsiElement(node)

/**
 * PSI element specifically for SSA values that provides references
 */
class MLIRSSAValueElement(node: ASTNode) : MLIRElement(node), PsiNamedElement {
    
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

