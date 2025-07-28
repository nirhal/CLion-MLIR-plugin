package org.komlir.intellijmlirplugin

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.komlir.intellijmlirplugin.psi.MLIRSSAValueReference

/**
 * Base PSI element implementation for MLIR
 */
open class MLIRElementImpl(node: ASTNode) : ASTWrapperPsiElement(node)

/**
 * PSI element specifically for SSA values that provides references
 */
class MLIRSSAValueElement(node: ASTNode) : MLIRElementImpl(node) {
    
    override fun getReference(): PsiReference? {
        return MLIRSSAValueReference(this)
    }
    
    fun getSSAValueName(): String? {
        return text?.removePrefix("%")
    }
}
