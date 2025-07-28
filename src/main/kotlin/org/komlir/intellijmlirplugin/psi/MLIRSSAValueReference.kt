package org.komlir.intellijmlirplugin.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.util.IncorrectOperationException
import org.komlir.intellijmlirplugin.MLIRSSAValueElement
import org.komlir.intellijmlirplugin.MLIRTokenTypes

/**
 * Reference implementation for MLIR SSA values
 * Handles navigation from SSA value usage to its definition
 */
class MLIRSSAValueReference(element: MLIRSSAValueElement) : PsiReferenceBase<MLIRSSAValueElement>(element) {

    override fun getRangeInElement(): TextRange {
        // The range covers the entire SSA value including the % prefix
        return TextRange(0, element.textLength)
    }

    override fun resolve(): MLIRSSAValueElement? {
        val ssaValueName = getSSAValueName() ?: return null
        val file = element.containingFile

        // Find the definition of this SSA value in the file
        return findSSAValueDefinition(file, ssaValueName)
    }

    override fun getVariants(): Array<Any> {
        val file = element.containingFile
        val allSSAValues = mutableListOf<String>()

        // Collect all SSA value definitions in the file for code completion
        collectSSAValueDefinitions(file, allSSAValues)

        return allSSAValues.toTypedArray()
    }

    @Throws(IncorrectOperationException::class)
    override fun handleElementRename(newElementName: String): PsiElement {
        // Handle renaming of SSA values
        // For now, return the current element - full rename support would require more infrastructure
        return element
    }

    private fun getSSAValueName(): String? {
        return element.text?.removePrefix("%")
    }

    private fun findSSAValueDefinition(file: PsiFile, ssaValueName: String): MLIRSSAValueElement? {
        var result: MLIRSSAValueElement? = null
        val targetText = "%$ssaValueName"

        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                // Look for SSA value definitions (assignments)
                // In MLIR, SSA values are defined like: %result = operation(...)
                if (element is MLIRSSAValueElement) {
                    if (element.text == targetText) {
                        // Check if this is a definition (followed by =)
                        if (isSSADefinition(element) && result == null) {
                            result = element
                            return
                        }
                    }
                }
                super.visitElement(element)
            }
        })

        return result
    }

    private fun collectSSAValueDefinitions(file: PsiFile, collector: MutableList<String>) {
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.node?.elementType == MLIRTokenTypes.SSA_VALUE) {
                    // Check if this is a definition
                    if (isSSADefinition(element)) {
                        val ssaName = element.text.removePrefix("%")
                        if (!collector.contains(ssaName)) {
                            collector.add(ssaName)
                        }
                    }
                }
                super.visitElement(element)
            }
        })
    }

    private fun isSSADefinition(ssaElement: PsiElement): Boolean {
        // Check if this SSA value is followed by an assignment operator
        var current: PsiElement? = ssaElement.nextSibling

        // Skip whitespace
        while (current != null && current.node?.elementType == com.intellij.psi.TokenType.WHITE_SPACE) {
            current = current.nextSibling
        }

        // Check if we find an = operator
        return current != null &&
               current.node?.elementType == MLIRTokenTypes.OPERATOR &&
               current.text.trim() == "="
    }
}
