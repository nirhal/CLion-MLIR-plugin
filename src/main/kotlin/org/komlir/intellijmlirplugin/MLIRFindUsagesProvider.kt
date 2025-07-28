package org.komlir.intellijmlirplugin

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls


class MLIRFindUsagesProvider : FindUsagesProvider {

    override fun getWordsScanner(): WordsScanner? {
        return DefaultWordsScanner(
            MLIRLexer(),
            MLIRTokenTypes.TokenSets.identifiers,
            MLIRTokenTypes.TokenSets.comments,
            MLIRTokenTypes.TokenSets.literals
        )
    }
    override fun canFindUsagesFor(element: PsiElement): Boolean {
        return element is MLIRSSAValueElement
    }

    override fun getType(element: PsiElement): @Nls String = when (element) {
        is MLIRSSAValueElement -> "SSA Value"
        else -> ""
    }

    override fun getDescriptiveName(element: PsiElement): @Nls String = when (element) {
        is MLIRSSAValueElement -> element.name ?: "Unnamed SSA Value"
        else -> element.text
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): @Nls String = when (element) {
        is MLIRSSAValueElement -> element.text
        else -> element.text
    }

    override fun getHelpId(element: PsiElement): String? = null
}