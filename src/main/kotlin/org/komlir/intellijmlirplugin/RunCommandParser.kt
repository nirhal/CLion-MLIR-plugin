package org.komlir.intellijmlirplugin

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

object RunCommandParser {
    fun parseCommand(element: PsiComment): String? {
        val command = element.text.trim().removePrefix("//").trim()
        if (!command.startsWith("RUN:")) return null
        return command.removePrefix("RUN:").trim()
    }

    fun searchCommand(element: PsiElement): String? {
        if (element is PsiComment) return parseCommand(element)
        return PsiTreeUtil.findChildrenOfType(element, PsiComment::class.java).firstNotNullOf {
            parseCommand(it)
        }
    }
}