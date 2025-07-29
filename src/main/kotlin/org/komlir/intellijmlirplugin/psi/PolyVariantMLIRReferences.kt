package org.komlir.intellijmlirplugin.psi

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.PsiTreeUtil

abstract class PolyVariantMLIRReference<T: MLIRElement>(
    element: T,
    range: TextRange,
) : PsiPolyVariantReferenceBase<T>(element, range) {
    abstract val typeText: String
    abstract val elementClass: Class<T>

    override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult?> {
        val name = element.name
        val file = element.containingFile

        // Find all symbol declarations that match
        val matches = PsiTreeUtil.findChildrenOfType(file, elementClass)
            .filter { it.name == name }

        return matches.map { PsiElementResolveResult(it) }.toTypedArray()
    }

    override fun getVariants(): Array<Any> {
        val file = element.containingFile
        val allSymbols = PsiTreeUtil.findChildrenOfType(file, elementClass)

        return allSymbols.mapNotNull { it.name }
            .map { LookupElementBuilder.create(it).withTypeText(typeText) }
            .toTypedArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        // Renaming is not supported for this reference type
        return element
    }
}

class MLIROperationReference(
    element: MLIROperationElement,
    range: TextRange
) : PolyVariantMLIRReference<MLIROperationElement>(element, range) {
    override val typeText: String = "Operation"
    override val elementClass: Class<MLIROperationElement> = MLIROperationElement::class.java
}

class MLIRTypeReference(
    element: MLIRTypeElement,
    range: TextRange
) : PolyVariantMLIRReference<MLIRTypeElement>(element, range) {
    override val typeText: String = "Type"
    override val elementClass: Class<MLIRTypeElement> = MLIRTypeElement::class.java
}

class MLIRAttributeReference(
    element: MLIRAttributeElement,
    range: TextRange
) : PolyVariantMLIRReference<MLIRAttributeElement>(element, range) {
    override val typeText: String = "Attribute"
    override val elementClass: Class<MLIRAttributeElement> = MLIRAttributeElement::class.java
}