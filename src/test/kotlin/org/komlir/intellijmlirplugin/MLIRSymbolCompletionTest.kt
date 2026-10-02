package org.komlir.intellijmlirplugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MLIRSymbolCompletionTest : BasePlatformTestCase() {
    fun testSymbolsDeclaredBeforeAndAfterCaretAreOffered() {
        com.intellij.testFramework.TestModeFlags.set(
            com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler.ourTestingAutopopup, true, testRootDisposable)
        myFixture.configureByText("symbols.mlir", """
            module {
              func.func private @first()
              func.func @caller() {
                func.call <caret>() : () -> ()
                return
              }
              func.func private @second()
            }
        """.trimIndent())
        myFixture.type('@')
        com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching("Typing @ did not open symbol completion", {
            myFixture.lookupElements?.any { it.lookupString == "@second" } == true
        }, 10)
        val names = myFixture.lookupElements!!.map { it.lookupString }
        assertTrue(names.toString(), names.containsAll(listOf("@first", "@caller", "@second")))
        myFixture.lookup.currentItem = myFixture.lookupElements!!.first { it.lookupString == "@second" }
        myFixture.finishLookup('\n')
        assertTrue(myFixture.editor.document.text.contains("func.call @second()"))
    }

    fun testPartialSymbolInsertionKeepsSingleAtSign() {
        myFixture.configureByText("symbols.mlir", """
            module {
              func.func private @first()
              func.func private @finalize()
              func.func @caller() {
                func.call @fi<caret>() : () -> ()
                return
              }
            }
        """.trimIndent())
        val items = myFixture.completeBasic()!!
        assertTrue(items.any { it.lookupString == "@first" })
        assertTrue(items.any { it.lookupString == "@finalize" })
        myFixture.lookup.currentItem = items.first { it.lookupString == "@first" }
        myFixture.finishLookup('\n')
        assertTrue(myFixture.editor.document.text.contains("func.call @first()"))
    }
}
