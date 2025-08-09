package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

object RunCommandParser {

    data class Command (
        val executable: String,
        val arguments: String
    )

    data class ParseResult(
        val mainCommand: Command,
        val pipedCommands: List<Command>
    )

    fun extractCommand(element: PsiComment): String? {
        val command = element.text.trim().removePrefix("//").trim()
        if (!command.startsWith("RUN:")) return null
        return command.removePrefix("RUN:").trim()
    }

    fun searchCommand(element: PsiElement): String? {
        if (element is PsiComment) return extractCommand(element)
        return PsiTreeUtil.findChildrenOfType(element, PsiComment::class.java).firstNotNullOf {
            extractCommand(it)
        }
    }

    fun searchAndParseCommand(element: PsiElement, filename: String): ParseResult? {
        val command = searchCommand(element)?.replace("%s", filename) ?: return null
        val mainCommandStr = command.takeWhile { it != '|' }.split(" ").map { it.trim() }
        val mainCommand = Command(
            executable = mainCommandStr.firstOrNull() ?: return null,
            arguments = mainCommandStr.drop(1).joinToString(" ")
        )
        val pipedCommands = command.split("|").takeIf { it.isNotEmpty() }?.drop(1)?.map { it.trim() }?.map {
            val parts = it.split(" ").map { part -> part.trim() }
            Command(
                executable = parts.firstOrNull() ?: return@searchAndParseCommand null,
                arguments = parts.drop(1).joinToString(" ")
            )
        } ?: listOf()
        return ParseResult(mainCommand, pipedCommands)
    }
}