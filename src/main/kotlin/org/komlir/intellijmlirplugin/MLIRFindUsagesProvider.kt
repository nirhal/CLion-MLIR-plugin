package org.komlir.intellijmlirplugin

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.komlir.intellijmlirplugin.psi.MLIRElement
import org.komlir.intellijmlirplugin.psi.MLIRSSAValueElement


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
        return element is MLIRElement
    }

    override fun getType(element: PsiElement): @Nls String = when (element as MLIRElement) {
        is MLIRSSAValueElement -> "SSA Value"
    }

    override fun getDescriptiveName(element: PsiElement): @Nls String = when (element as MLIRElement) {
        is MLIRSSAValueElement -> element.name ?: "Unnamed SSA Value"
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): @Nls String = when (element as MLIRElement) {
        is MLIRSSAValueElement -> element.text
    }

    override fun getHelpId(element: PsiElement): String? = null
}