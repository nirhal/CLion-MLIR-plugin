package org.komlir.intellijmlirplugin

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.komlir.intellijmlirplugin.psi.MLIRAttributeElement
import org.komlir.intellijmlirplugin.psi.MLIRFile
import org.komlir.intellijmlirplugin.psi.MLIROperationElement
import org.komlir.intellijmlirplugin.psi.MLIRSSAValueElement
import org.komlir.intellijmlirplugin.psi.MLIRSymbolElement
import org.komlir.intellijmlirplugin.psi.MLIRTypeElement

class MLIRParserDefinition : ParserDefinition {
    
    companion object {
        val WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE)
        val COMMENTS = TokenSet.create(MLIRTokenTypes.LINE_COMMENT)
        val FILE = IFileElementType(MLIRLanguage)
    }
    
    override fun createLexer(project: Project?): Lexer = MLIRLexer()
    
    override fun createParser(project: Project?): PsiParser = MLIRParser()
    
    override fun getFileNodeType(): IFileElementType = FILE
    
    override fun getCommentTokens(): TokenSet = COMMENTS
    
    override fun getWhitespaceTokens(): TokenSet = WHITE_SPACES
    
    override fun getStringLiteralElements(): TokenSet = TokenSet.create(MLIRTokenTypes.STRING_LITERAL)
    
    override fun createElement(node: ASTNode): PsiElement {
        return when (node.elementType) {
            MLIRTokenTypes.SSA_VALUE -> MLIRSSAValueElement(node)
            MLIRTokenTypes.SYMBOL_REF -> MLIRSymbolElement(node)
            MLIRTokenTypes.OPERATION -> MLIROperationElement(node)
            MLIRTokenTypes.TYPE -> MLIRTypeElement(node)
            MLIRTokenTypes.ATTRIBUTE -> MLIRAttributeElement(node)
            else -> error("Unknown element type: ${node.elementType}")
        }
    }
    
    override fun createFile(viewProvider: FileViewProvider): PsiFile {
        return MLIRFile(viewProvider)
    }
}
