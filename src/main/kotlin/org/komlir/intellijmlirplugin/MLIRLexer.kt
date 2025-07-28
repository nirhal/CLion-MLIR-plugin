package org.komlir.intellijmlirplugin

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import com.intellij.psi.TokenType

class MLIRLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var startOffset: Int = 0
    private var endOffset: Int = 0
    private var currentOffset: Int = 0
    private var tokenType: IElementType? = null
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private var angleBracketDepth: Int = 0  // Track depth of nested angle brackets

    companion object {
        const val STATE_NORMAL = 0
        const val STATE_IN_ANGLE_BRACKETS = 1
    }

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        this.currentOffset = startOffset
        this.angleBracketDepth = if (initialState == STATE_IN_ANGLE_BRACKETS) 1 else 0
        advance()
    }

    override fun getState(): Int = if (angleBracketDepth > 0) STATE_IN_ANGLE_BRACKETS else STATE_NORMAL

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun advance() {
        if (currentOffset >= endOffset) {
            tokenType = null
            return
        }

        tokenStart = currentOffset
        val char = buffer[currentOffset]

        when {
            char.isWhitespace() -> {
                consumeWhitespace()
                tokenType = TokenType.WHITE_SPACE
            }
            char == '/' && currentOffset + 1 < endOffset && buffer[currentOffset + 1] == '/' -> {
                consumeLineComment()
                tokenType = MLIRTokenTypes.LINE_COMMENT
            }
            char == '"' -> {
                consumeString()
                tokenType = MLIRTokenTypes.STRING_LITERAL
            }
            char.isDigit() || (char == '-' && currentOffset + 1 < endOffset && buffer[currentOffset + 1].isDigit()) -> {
                consumeNumber()
                tokenType = MLIRTokenTypes.NUMBER
            }
            char == '%' -> {
                consumeSSAValue()
                tokenType = MLIRTokenTypes.SSA_VALUE
            }
            char == '@' -> {
                consumeSymbolRef()
                tokenType = MLIRTokenTypes.SYMBOL_REF
            }
            char == '#' -> {
                consumeAttribute()
                tokenType = MLIRTokenTypes.ATTRIBUTE
            }
            char == '!' -> {
                consumeType()
                tokenType = MLIRTokenTypes.TYPE
            }
            char.isLetter() || char == '_' -> {
                val word = consumeIdentifier()
                tokenType = when {
                    // Inside angle brackets: prioritize type recognition
                    angleBracketDepth > 0 -> when {
                        // MLIR built-in types - expanded regex for comprehensive type matching
                        word.matches(Regex("^(i[0-9]+|ui[0-9]+|si[0-9]+|f16|f32|f64|bf16|f80|f128|index|none|complex)$")) -> MLIRTokenTypes.TYPE

                        // Complex types (memref, tensor, vector) - can be nested
                        word in setOf("memref", "tensor", "vector") -> MLIRTokenTypes.TYPE

                        // Dimension separator 'x' in shaped types
                        word == "x" -> MLIRTokenTypes.OPERATOR

                        // Any other identifier inside angle brackets could be a custom type
                        word.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*$")) -> MLIRTokenTypes.TYPE

                        else -> MLIRTokenTypes.IDENTIFIER
                    }

                    // Outside angle brackets: normal recognition rules
                    else -> when {
                        // MLIR built-in types
                        word.matches(Regex("^(i[0-9]+|ui[0-9]+|si[0-9]+|f16|f32|f64|bf16|f80|f128|index|none|complex)$")) -> MLIRTokenTypes.TYPE

                        // MLIR operations (dialect.operation format or standalone operations)
                        word.contains('.') || word in setOf("module", "unrealized_conversion_cast") -> MLIRTokenTypes.OPERATION

                        // Complex types (memref, tensor, vector)
                        word in setOf("memref", "tensor", "vector") -> MLIRTokenTypes.TYPE

                        // Dimension separator 'x' in shaped types (outside angle brackets, it's just an identifier)
                        word == "x" -> MLIRTokenTypes.IDENTIFIER

                        else -> MLIRTokenTypes.IDENTIFIER
                    }
                }
            }
            char in "(){}[]<>" -> {
                currentOffset++
                tokenType = when (char) {
                    '(' -> MLIRTokenTypes.LPAREN
                    ')' -> MLIRTokenTypes.RPAREN
                    '{' -> MLIRTokenTypes.LBRACE
                    '}' -> MLIRTokenTypes.RBRACE
                    '[' -> MLIRTokenTypes.LBRACKET
                    ']' -> MLIRTokenTypes.RBRACKET
                    '<' -> {
                        angleBracketDepth++ // Entering angle brackets
                        MLIRTokenTypes.LT
                    }
                    '>' -> {
                        if (angleBracketDepth > 0) {
                            angleBracketDepth-- // Exiting angle brackets
                            MLIRTokenTypes.GT
                        } else {
                            currentOffset++
                            MLIRTokenTypes.GT
                        }
                    }
                    else -> MLIRTokenTypes.IDENTIFIER
                }
            }
            char in ",:;=+-*/?" -> {
                currentOffset++
                tokenType = MLIRTokenTypes.OPERATOR
            }
            else -> {
                currentOffset++
                tokenType = TokenType.BAD_CHARACTER
            }
        }

        tokenEnd = currentOffset
    }

    private fun consumeWhitespace() {
        while (currentOffset < endOffset && buffer[currentOffset].isWhitespace()) {
            currentOffset++
        }
    }

    private fun consumeLineComment() {
        currentOffset += 2 // Skip //
        while (currentOffset < endOffset && buffer[currentOffset] != '\n') {
            currentOffset++
        }
    }

    private fun consumeString() {
        currentOffset++ // Skip opening quote
        while (currentOffset < endOffset && buffer[currentOffset] != '"') {
            if (buffer[currentOffset] == '\\') {
                currentOffset += 2 // Skip escaped character
            } else {
                currentOffset++
            }
        }
        if (currentOffset < endOffset) {
            currentOffset++ // Skip closing quote
        }
    }

    private fun consumeNumber() {
        if (buffer[currentOffset] == '-') {
            currentOffset++
        }

        // Handle hexadecimal numbers (0x or 0X prefix)
        if (currentOffset + 1 < endOffset &&
            buffer[currentOffset] == '0' &&
            (buffer[currentOffset + 1] == 'x' || buffer[currentOffset + 1] == 'X')) {
            currentOffset += 2 // Skip "0x" or "0X"
            while (currentOffset < endOffset &&
                   (buffer[currentOffset].isDigit() ||
                    buffer[currentOffset].lowercaseChar() in 'a'..'f')) {
                currentOffset++
            }
            return
        }

        // Handle decimal numbers
        while (currentOffset < endOffset && (buffer[currentOffset].isDigit() || buffer[currentOffset] == '.')) {
            currentOffset++
        }
        // Handle scientific notation
        if (currentOffset < endOffset && (buffer[currentOffset] == 'e' || buffer[currentOffset] == 'E')) {
            currentOffset++
            if (currentOffset < endOffset && (buffer[currentOffset] == '+' || buffer[currentOffset] == '-')) {
                currentOffset++
            }
            while (currentOffset < endOffset && buffer[currentOffset].isDigit()) {
                currentOffset++
            }
        }
    }

    private fun consumeSSAValue() {
        currentOffset++ // Skip %
        while (currentOffset < endOffset && (buffer[currentOffset].isLetterOrDigit() || buffer[currentOffset] in "_.-")) {
            currentOffset++
        }
    }

    private fun consumeSymbolRef() {
        currentOffset++ // Skip @
        while (currentOffset < endOffset && (buffer[currentOffset].isLetterOrDigit() || buffer[currentOffset] in "_.-")) {
            currentOffset++
        }
    }

    private fun consumeAttribute() {
        currentOffset++ // Skip #
        while (currentOffset < endOffset && (buffer[currentOffset].isLetterOrDigit() || buffer[currentOffset] in "_.-")) {
            currentOffset++
        }
    }

    private fun consumeType() {
        currentOffset++ // Skip !
        while (currentOffset < endOffset && (buffer[currentOffset].isLetterOrDigit() || buffer[currentOffset] in "_.-")) {
            currentOffset++
        }
    }

    private fun consumeIdentifier(): String {
        val start = currentOffset

        // Special handling inside angle brackets to separate 'x' from following identifiers
        if (angleBracketDepth > 0) {
            // First character
            if (currentOffset < endOffset && (buffer[currentOffset].isLetter() || buffer[currentOffset] == '_')) {
                currentOffset++

                // If this is just 'x', stop here to allow it to be treated as dimension separator
                if (currentOffset - start == 1 && buffer[start] == 'x') {
                    return buffer.subSequence(start, currentOffset).toString()
                }

                // Continue consuming for other identifiers
                while (currentOffset < endOffset && (buffer[currentOffset].isLetterOrDigit() || buffer[currentOffset] in "_.-")) {
                    currentOffset++
                }
            }
        } else {
            // Normal identifier consumption outside angle brackets
            while (currentOffset < endOffset && (buffer[currentOffset].isLetterOrDigit() || buffer[currentOffset] in "_.-")) {
                currentOffset++
            }
        }

        return buffer.subSequence(start, currentOffset).toString()
    }

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = endOffset
}
