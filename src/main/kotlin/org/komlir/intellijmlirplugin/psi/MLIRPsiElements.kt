package org.komlir.intellijmlirplugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
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
        return MLIRSSAValueReference(this, TextRange(0, textLength))
    }

    override fun getName(): String? {
        return text?.removePrefix("%")
    }

    override fun setName(name: String): PsiElement? {
        return replace(MLIRElementFactory.createSSAValue(project, name))
    }
}

class MLIRSymbolElement(node: ASTNode) : MLIRElement(node), PsiNamedElement {

    override fun getReference(): PsiReference? {
        return MLIRSymbolReference(this, TextRange(0, textLength))
    }

    override fun getName(): String? {
        return text?.removePrefix("@")
    }

    override fun setName(name: String): PsiElement? {
        return replace(MLIRElementFactory.createSymbol(project, name))
    }
}

class MLIROperationElement(node: ASTNode) : MLIRElement(node), PsiNamedElement {

    override fun getReference(): PsiReference? {
        return MLIROperationReference(this, TextRange(0, textLength))
    }

    override fun getName(): String? {
        return text
    }

    override fun setName(name: String): PsiElement? = null
}

class MLIRTypeElement(node: ASTNode) : MLIRElement(node), PsiNamedElement {

    override fun getReference(): PsiReference? {
        return MLIRTypeReference(this, TextRange(0, textLength))
    }

    override fun getName(): String? {
        return text
    }

    override fun setName(name: String): PsiElement? = null
}

class MLIRAttributeElement(node: ASTNode) : MLIRElement(node), PsiNamedElement {

    override fun getReference(): PsiReference? {
        return MLIRAttributeReference(this, TextRange(0, textLength))
    }

    override fun getName(): String? {
        return text
    }

    override fun setName(name: String): PsiElement? = null
}