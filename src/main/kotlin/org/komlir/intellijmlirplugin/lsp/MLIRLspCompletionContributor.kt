package org.komlir.intellijmlirplugin.lsp

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.psi.util.PsiTreeUtil
import org.komlir.intellijmlirplugin.psi.MLIROperationCompletionItem
import org.komlir.intellijmlirplugin.psi.MLIROperationElement

/** Decide using this completion's matching results, not merely whether a server is running. */
class MLIRLspCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (PsiTreeUtil.getParentOfType(parameters.position, MLIROperationElement::class.java, false) == null) return
        val candidates = result.runRemainingContributors(parameters, false)
        val hasServerResults = candidates.any {
            it.lookupElement.`as`(MLIROperationCompletionItem::class.java)?.fromLanguageServer == true
        }
        for (candidate in candidates) {
            val origin = candidate.lookupElement.`as`(MLIROperationCompletionItem::class.java)
            if (!hasServerResults || origin == null || origin.fromLanguageServer) result.passResult(candidate)
        }
        result.stopHere()
    }
}
