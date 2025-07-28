package org.komlir.intellijmlirplugin

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.ui.JBColor
import java.awt.Color

class MLIRSyntaxHighlighter : SyntaxHighlighterBase() {
    
    companion object {
        @JvmField val IDENTIFIER = TextAttributesKey.createTextAttributesKey(
            "MLIR_IDENTIFIER", DefaultLanguageHighlighterColors.INSTANCE_METHOD
        )
        @JvmField val STRING = TextAttributesKey.createTextAttributesKey(
            "MLIR_STRING", DefaultLanguageHighlighterColors.STRING
        )
        @JvmField val NUMBER = TextAttributesKey.createTextAttributesKey(
            "MLIR_NUMBER", DefaultLanguageHighlighterColors.NUMBER
        )
        @JvmField val LINE_COMMENT = TextAttributesKey.createTextAttributesKey(
            "MLIR_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT
        )
        @JvmField val SSA_VALUE = TextAttributesKey.createTextAttributesKey(
            "MLIR_SSA_VALUE", DefaultLanguageHighlighterColors.CONSTANT
        )
        @JvmField val SYMBOL_REF = TextAttributesKey.createTextAttributesKey(
            "MLIR_SYMBOL_REF", DefaultLanguageHighlighterColors.METADATA
        )
        @JvmField val ATTRIBUTE = TextAttributesKey.createTextAttributesKey(
            "MLIR_ATTRIBUTE", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION
        )
        @JvmField val TYPE = TextAttributesKey.createTextAttributesKey(
            "MLIR_TYPE",
            TextAttributes().apply {
                foregroundColor = JBColor(Color(0x00, 0x7E, 0x8A), Color(0x67, 0xB9, 0xAC))
            }
        )
        @JvmField val OPERATION = TextAttributesKey.createTextAttributesKey(
            "MLIR_OPERATION", DefaultLanguageHighlighterColors.KEYWORD
        )
        @JvmField val OPERATOR = TextAttributesKey.createTextAttributesKey(
            "MLIR_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN
        )
        @JvmField val PARENTHESES = TextAttributesKey.createTextAttributesKey(
            "MLIR_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES
        )
        @JvmField val BRACES = TextAttributesKey.createTextAttributesKey(
            "MLIR_BRACES", DefaultLanguageHighlighterColors.BRACES
        )
        @JvmField val BRACKETS = TextAttributesKey.createTextAttributesKey(
            "MLIR_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS
        )
        @JvmField val BAD_CHARACTER = TextAttributesKey.createTextAttributesKey(
            "MLIR_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER
        )
    }

    override fun getHighlightingLexer(): Lexer = MLIRLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> {
        return when (tokenType) {
            MLIRTokenTypes.IDENTIFIER -> arrayOf(IDENTIFIER)
            MLIRTokenTypes.STRING_LITERAL -> arrayOf(STRING)
            MLIRTokenTypes.NUMBER -> arrayOf(NUMBER)
            MLIRTokenTypes.LINE_COMMENT -> arrayOf(LINE_COMMENT)
            MLIRTokenTypes.SSA_VALUE -> arrayOf(SSA_VALUE)
            MLIRTokenTypes.SYMBOL_REF -> arrayOf(SYMBOL_REF)
            MLIRTokenTypes.ATTRIBUTE -> arrayOf(ATTRIBUTE)
            MLIRTokenTypes.TYPE -> arrayOf(TYPE)
            MLIRTokenTypes.OPERATION -> arrayOf(OPERATION)
            MLIRTokenTypes.OPERATOR -> arrayOf(OPERATOR)
            MLIRTokenTypes.LPAREN, MLIRTokenTypes.RPAREN -> arrayOf(PARENTHESES)
            MLIRTokenTypes.LBRACE, MLIRTokenTypes.RBRACE -> arrayOf(BRACES)
            MLIRTokenTypes.LBRACKET, MLIRTokenTypes.RBRACKET -> arrayOf(BRACKETS)
            MLIRTokenTypes.LT, MLIRTokenTypes.GT -> arrayOf(BRACKETS)
            TokenType.BAD_CHARACTER -> arrayOf(BAD_CHARACTER)
            else -> emptyArray()
        }
    }
}
