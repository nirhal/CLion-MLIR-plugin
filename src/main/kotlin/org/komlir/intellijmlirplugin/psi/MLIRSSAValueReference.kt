package org.komlir.intellijmlirplugin.psi

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.komlir.intellijmlirplugin.MLIRTokenTypes

class MLIRSSAValueReference(
    element: MLIRSSAValueElement,
    range: TextRange
) : PsiReferenceBase<MLIRSSAValueElement>(element, range) {

    override fun resolve(): MLIRSSAValueElement? {
        val ssaValueName = element.name ?: return null
        val file = element.containingFile
        return PsiTreeUtil.findChildrenOfType(file, MLIRSSAValueElement::class.java).firstOrNull {
            it.name == ssaValueName && it.isSSADefinition()
        }
    }

    override fun getVariants(): Array<Any> {
        val file = element.containingFile
        val allSSAValues = PsiTreeUtil.findChildrenOfType(file, MLIRSSAValueElement::class.java).filter {
            it.isSSADefinition()
        }
        return allSSAValues.map { LookupElementBuilder.create("%${it.name}").withTypeText("SSA Value") }.toTypedArray()
    }

    @Throws(IncorrectOperationException::class)
    override fun handleElementRename(newElementName: String): PsiElement {
        return element.replace(MLIRElementFactory.createSSAValue(element.project, newElementName))
    }

    private fun MLIRSSAValueElement.isSSADefinition(): Boolean {
        // Check if this SSA value is followed by an assignment operator
        var current: PsiElement? = nextSibling

        // Skip whitespace
        while (current != null && current.node?.elementType == TokenType.WHITE_SPACE) {
            current = current.nextSibling
        }

        // Check if we find an = operator
        return current != null &&
               current.node?.elementType == MLIRTokenTypes.OPERATOR &&
               current.text.trim() == "="
    }
}
