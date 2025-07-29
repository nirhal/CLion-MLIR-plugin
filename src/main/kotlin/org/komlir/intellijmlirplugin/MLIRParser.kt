package org.komlir.intellijmlirplugin

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType

/**
 * Simple parser for MLIR that creates a basic PSI structure
 */
class MLIRParser : PsiParser {
    
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val marker = builder.mark()
        
        // Parse all tokens and create PSI elements
        while (!builder.eof()) {
            val tokenType = builder.tokenType

            when (tokenType) {
                MLIRTokenTypes.SSA_VALUE -> {
                    // Create a specific PSI element for SSA values
                    val ssaMarker = builder.mark()
                    builder.advanceLexer()
                    ssaMarker.done(MLIRTokenTypes.SSA_VALUE)
                }
                MLIRTokenTypes.SYMBOL_REF -> {
                    // Create a specific PSI element for symbol references
                    val symbolMarker = builder.mark()
                    builder.advanceLexer()
                    symbolMarker.done(MLIRTokenTypes.SYMBOL_REF)
                }
                MLIRTokenTypes.OPERATION -> {
                    // Create a specific PSI element for symbol references
                    val symbolMarker = builder.mark()
                    builder.advanceLexer()
                    symbolMarker.done(MLIRTokenTypes.OPERATION)
                }
                else -> {
                    // For all other tokens, just advance
                    builder.advanceLexer()
                }
            }
        }
        
        marker.done(root)
        return builder.treeBuilt
    }
}
