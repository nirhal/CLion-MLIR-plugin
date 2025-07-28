package org.komlir.intellijmlirplugin.psi

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.komlir.intellijmlirplugin.MLIRLanguage
import org.komlir.intellijmlirplugin.MLIRSSAValueElement
import org.komlir.intellijmlirplugin.MLIRTokenTypes

/**
 * Reference provider for MLIR SSA values
 * This tells IntelliJ when and how to create references for SSA values
 */
class MLIRSSAValueReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        // Only provide references for SSA value tokens
        if (element is MLIRSSAValueElement) {
            val reference = MLIRSSAValueReference(element)
            return arrayOf(reference)
        }
        return PsiReference.EMPTY_ARRAY
    }
}

/**
 * Reference contributor that registers the SSA value reference provider
 */
class MLIRReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // Register the provider for all elements in MLIR files, then filter in the provider
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(MLIRSSAValueElement::class.java),
            MLIRSSAValueReferenceProvider()
        )
    }
}
