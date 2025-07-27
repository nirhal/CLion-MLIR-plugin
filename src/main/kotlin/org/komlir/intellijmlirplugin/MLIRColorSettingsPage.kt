package org.komlir.intellijmlirplugin

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class MLIRColorSettingsPage : ColorSettingsPage {

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "MLIR"

    override fun getIcon(): Icon? = null

    override fun getHighlighter(): SyntaxHighlighter = MLIRSyntaxHighlighter()

    override fun getDemoText(): String = DEMO_TEXT

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null
}

private val DESCRIPTORS = arrayOf(
    AttributesDescriptor("Operation", MLIRSyntaxHighlighter.OPERATION),
    AttributesDescriptor("Type", MLIRSyntaxHighlighter.TYPE),
    AttributesDescriptor("SSA value", MLIRSyntaxHighlighter.SSA_VALUE),
    AttributesDescriptor("Symbol reference", MLIRSyntaxHighlighter.SYMBOL_REF),
    AttributesDescriptor("Attribute", MLIRSyntaxHighlighter.ATTRIBUTE),
    AttributesDescriptor("Number", MLIRSyntaxHighlighter.NUMBER),
    AttributesDescriptor("String", MLIRSyntaxHighlighter.STRING),
    AttributesDescriptor("Line comment", MLIRSyntaxHighlighter.LINE_COMMENT),
    AttributesDescriptor("Block comment", MLIRSyntaxHighlighter.BLOCK_COMMENT),
    AttributesDescriptor("Operator", MLIRSyntaxHighlighter.OPERATOR),
    AttributesDescriptor("Parentheses", MLIRSyntaxHighlighter.PARENTHESES),
    AttributesDescriptor("Braces", MLIRSyntaxHighlighter.BRACES),
    AttributesDescriptor("Brackets", MLIRSyntaxHighlighter.BRACKETS),
    AttributesDescriptor("Identifier", MLIRSyntaxHighlighter.IDENTIFIER),
    AttributesDescriptor("Bad character", MLIRSyntaxHighlighter.BAD_CHARACTER)
)

private val DEMO_TEXT = """
    // MLIR Example demonstrating syntax highlighting
    module {
      func.func @main(%arg0: memref<10xi32>) -> i32 {
        %c0 = arith.constant 0 : index
        %c1 = arith.constant 1 : i32
        %c42 = arith.constant 42 : i32
        %hex_val = arith.constant 0xFF : i32
        
        // Load from memory reference
        %val = memref.load %arg0[%c0] : memref<10xi32>
        
        // Arithmetic operations
        %sum = arith.addi %val, %c1 : i32
        %result = arith.muli %sum, %c42 : i32
        
        /* Multi-line comment
           showing block comment highlighting */
        custom_dialect.my_operation %result {
          attr = #my_attr<"value">
        } : !custom.type
        
        return %result : i32
      }
    }
""".trimIndent()