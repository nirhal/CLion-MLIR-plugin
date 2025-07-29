package org.komlir.intellijmlirplugin

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.komlir.intellijmlirplugin.psi.MLIRAttributeElement
import org.komlir.intellijmlirplugin.psi.MLIRElement
import org.komlir.intellijmlirplugin.psi.MLIROperationElement
import org.komlir.intellijmlirplugin.psi.MLIRSSAValueElement
import org.komlir.intellijmlirplugin.psi.MLIRSymbolElement
import org.komlir.intellijmlirplugin.psi.MLIRTypeElement


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
        is MLIRSymbolElement -> "Symbol"
        is MLIROperationElement -> "Operation"
        is MLIRTypeElement -> "Type"
        is MLIRAttributeElement -> "Attribute"
    }

    override fun getDescriptiveName(element: PsiElement): @Nls String = when (element as MLIRElement) {
        is MLIRSSAValueElement -> element.name ?: "Unnamed SSA Value"
        is MLIRSymbolElement -> element.name ?: "Unnamed Symbol"
        is MLIROperationElement -> element.name ?: "Unnamed Operation"
        is MLIRTypeElement -> element.name ?: "Unnamed Type"
        is MLIRAttributeElement -> element.name ?: "Unnamed Attribute"
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): @Nls String = when (element as MLIRElement) {
        is MLIRSSAValueElement -> element.text
        is MLIRSymbolElement -> element.text
        is MLIROperationElement -> element.text
        is MLIRAttributeElement -> element.text
        is MLIRTypeElement -> element.text
    }

    override fun getHelpId(element: PsiElement): String? = null
}