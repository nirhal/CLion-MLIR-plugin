package org.komlir.intellijmlirplugin.psi

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.PsiTreeUtil

class MLIROperationReference(
    element: MLIROperationElement,
    range: TextRange
) : PsiPolyVariantReferenceBase<MLIROperationElement>(element, range) {

    override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult?> {
        val name = element.name
        val file = element.containingFile

        // Find all symbol declarations that match
        val matches = PsiTreeUtil.findChildrenOfType(file, MLIROperationElement::class.java)
            .filter { it.name == name }

        return matches.map { PsiElementResolveResult(it) }.toTypedArray()
    }

    override fun getVariants(): Array<Any> {
        val file = element.containingFile
        val allSymbols = PsiTreeUtil.findChildrenOfType(file, MLIROperationElement::class.java)

        return allSymbols.mapNotNull { it.name }
            .map { LookupElementBuilder.create(it).withTypeText("Operation") }
            .toTypedArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        return element
    }
}