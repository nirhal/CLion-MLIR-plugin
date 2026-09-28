package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/** Parses the supported RUN subset without invoking a shell. */
object RunCommandParser {
    data class Command(val executable: String, val arguments: List<String>)
    data class Pipeline(val commands: List<Command>, val line: Int, val source: String)
    data class TestFile(val path: String, val pipelines: List<Pipeline>)
    class ParseException(message: String) : IllegalArgumentException(message)

    fun extractCommand(element: PsiComment): String? = extractCommand(element.text)

    private fun extractCommand(text: String): String? {
        val comment = text.trim().removePrefix("//").trim()
        return if (comment.startsWith("RUN:")) comment.removePrefix("RUN:").trim() else null
    }

    fun parse(element: PsiElement, filename: String): TestFile {
        val comments = if (element is PsiComment) listOf(element)
        else PsiTreeUtil.findChildrenOfType(element, PsiComment::class.java).sortedBy { it.textOffset }
        val fileText = element.containingFile.text
        return parseComments(comments.map {
            (fileText.take(it.textOffset).count { c -> c == '\n' } + 1) to it.text
        }, filename)
    }

    internal fun parseComments(comments: List<Pair<Int, String>>, filename: String): TestFile {
        val pipelines = mutableListOf<Pipeline>()
        var pending = ""
        var startLine = 0
        var lastLine = 0
        for ((line, comment) in comments) {
            val directive = comment.trim().removePrefix("//").trim()
            if (Regex("^(REQUIRES|UNSUPPORTED|XFAIL|DEFINE|REDEFINE):").containsMatchIn(directive)) {
                throw ParseException("Line $line: unsupported test directive '${directive.substringBefore(':')}'.")
            }
            val command = extractCommand(comment) ?: continue
            if (pending.isNotEmpty() && line != lastLine + 1) {
                throw ParseException("Line $startLine: a continued RUN must be followed by another RUN line.")
            }
            if (pending.isEmpty()) startLine = line
            val combined = pending + command
            val trailingSlashes = combined.takeLastWhile { it == '\\' }.length
            if (trailingSlashes % 2 == 1) {
                pending = combined.dropLast(1) + " "
                lastLine = line
                continue
            }
            try {
                pipelines += Pipeline(tokenize(combined, filename), startLine, combined)
            } catch (e: ParseException) {
                throw ParseException("Line $startLine: ${e.message}")
            }
            pending = ""
        }
        if (pending.isNotEmpty()) throw ParseException("Line $startLine: unfinished RUN continuation.")
        if (pipelines.isEmpty()) throw ParseException("No RUN directives found in this file.")
        return TestFile(filename, pipelines)
    }

    private fun tokenize(text: String, filename: String): List<Command> {
        val commands = mutableListOf<Command>()
        val words = mutableListOf<String>()
        val word = StringBuilder()
        var started = false
        var quote: Char? = null
        var i = 0
        fun finishWord() {
            if (started) words += word.toString()
            word.setLength(0)
            started = false
        }
        fun finishCommand() {
            finishWord()
            if (words.isEmpty() || words.first().isEmpty()) throw ParseException("Empty command in RUN pipeline.")
            if (words.first() in setOf("not", "env") || '=' in words.first()) {
                throw ParseException("Shell helpers and environment assignments are not supported: ${words.first()}")
            }
            commands += Command(words.first(), words.drop(1))
            words.clear()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                c == '%' -> {
                    when (text.getOrNull(i + 1)) {
                        's' -> word.append(filename)
                        '%' -> word.append('%')
                        else -> throw ParseException("Unsupported substitution at '${text.substring(i)}'; supported: %s and %%.")
                    }
                    started = true
                    i += 2
                    continue
                }
                c == '\\' && quote != '\'' -> {
                    val next = text.getOrNull(i + 1) ?: throw ParseException("Trailing escape in RUN command.")
                    if (quote == '"' && next !in "\"\\$`") {
                        word.append(c)
                    } else {
                        word.append(next)
                        i++
                    }
                    started = true
                }
                c == quote -> quote = null
                quote == null && c in "\"'" -> { quote = c; started = true }
                quote != '\'' && c in "$`" -> throw ParseException("Shell expansion is not supported: $c")
                quote != null -> word.append(c)
                c.isWhitespace() -> finishWord()
                c == '|' -> finishCommand()
                c in "<>&;()" -> throw ParseException("Unsupported shell operator: $c")
                c in "*?[]{}~" -> throw ParseException("Shell expansion is not supported: $c (quote literal arguments).")
                else -> { word.append(c); started = true }
            }
            i++
        }
        if (quote != null) throw ParseException("Unterminated quote in RUN command.")
        finishCommand()
        return commands
    }
}
