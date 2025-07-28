package org.komlir.intellijmlirplugin

import com.intellij.psi.tree.IElementType

object MLIRTokenTypes {
    @JvmField val KEYWORD = IElementType("MLIR_KEYWORD", MLIRLanguage)
    @JvmField val IDENTIFIER = IElementType("MLIR_IDENTIFIER", MLIRLanguage)
    @JvmField val STRING_LITERAL = IElementType("MLIR_STRING_LITERAL", MLIRLanguage)
    @JvmField val NUMBER = IElementType("MLIR_NUMBER", MLIRLanguage)
    @JvmField val LINE_COMMENT = IElementType("MLIR_LINE_COMMENT", MLIRLanguage)
    @JvmField val SSA_VALUE = IElementType("MLIR_SSA_VALUE", MLIRLanguage)
    @JvmField val SYMBOL_REF = IElementType("MLIR_SYMBOL_REF", MLIRLanguage)
    @JvmField val ATTRIBUTE = IElementType("MLIR_ATTRIBUTE", MLIRLanguage)
    @JvmField val TYPE = IElementType("MLIR_TYPE", MLIRLanguage)
    @JvmField val OPERATION = IElementType("MLIR_OPERATION", MLIRLanguage)
    @JvmField val OPERATOR = IElementType("MLIR_OPERATOR", MLIRLanguage)
    @JvmField val LPAREN = IElementType("MLIR_LPAREN", MLIRLanguage)
    @JvmField val RPAREN = IElementType("MLIR_RPAREN", MLIRLanguage)
    @JvmField val LBRACE = IElementType("MLIR_LBRACE", MLIRLanguage)
    @JvmField val RBRACE = IElementType("MLIR_RBRACE", MLIRLanguage)
    @JvmField val LBRACKET = IElementType("MLIR_LBRACKET", MLIRLanguage)
    @JvmField val RBRACKET = IElementType("MLIR_RBRACKET", MLIRLanguage)
    @JvmField val LT = IElementType("MLIR_LT", MLIRLanguage)
    @JvmField val GT = IElementType("MLIR_GT", MLIRLanguage)
}
