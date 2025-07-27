package org.komlir.intellijmlirplugin

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class MLIRBraceMatcher : PairedBraceMatcher {
    
    companion object {
        private val PAIRS = arrayOf(
            BracePair(MLIRTokenTypes.LPAREN, MLIRTokenTypes.RPAREN, false),
            BracePair(MLIRTokenTypes.LBRACE, MLIRTokenTypes.RBRACE, true),
            BracePair(MLIRTokenTypes.LBRACKET, MLIRTokenTypes.RBRACKET, false),
            BracePair(MLIRTokenTypes.LT, MLIRTokenTypes.GT, false)
        )
    }
    
    override fun getPairs(): Array<BracePair> = PAIRS
    
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean {
        return true
    }
    
    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int {
        return openingBraceOffset
    }
}
