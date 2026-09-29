package org.komlir.intellijmlirplugin.lsp

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.execution.ParametersListUtil
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path

class MLIRLspIntegrationTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl()

    fun testOpenEditAndDisableWithNativeLspClient() {
        withServer("// LSP_TEST_ERROR\nmodule {}\n") {
            PlatformTestUtil.waitWithEventsDispatching("Server diagnostic did not reach the editor", {
                myFixture.doHighlighting().any { it.description == "MLIR LSP integration diagnostic" }
            }, 15)
            WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText("module {}\n") }
            PlatformTestUtil.waitWithEventsDispatching("Diagnostic was not cleared after an unsaved edit", {
                myFixture.doHighlighting().none { it.description == "MLIR LSP integration diagnostic" }
            }, 15)
        }
    }

    fun testOperationCompletionShowsUnseenNamesAndKeepsDialectOnInsertion() {
        withServer("// LSP_TEST_ERROR\nmodule {}\n") {
            // Initialization finishes before the IDE has necessarily opened the document on the server.
            PlatformTestUtil.waitWithEventsDispatching("Document was not synchronized to the server", {
                myFixture.doHighlighting().any { it.description == "MLIR LSP integration diagnostic" }
            }, 15)
            for ((prefix, chosen) in listOf("llvm." to "sub", "llvm.ad" to "add",
                "llvm.intr." to "intr.sqrt")) {
                WriteCommandAction.runWriteCommandAction(project) {
                    myFixture.editor.document.setText("module {\n  $prefix\n}\n")
                }
                com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
                myFixture.editor.caretModel.moveToOffset("module {\n  $prefix".length)
                val suggestions = myFixture.completeBasic()
                if (suggestions == null) {
                    // CLion inserts a unique match automatically for partial names.
                    assertEquals("module {\n  llvm.$chosen\n}\n", myFixture.editor.document.text)
                    continue
                }
                assertTrue("Missing '$chosen' after '$prefix': ${suggestions.map { it.lookupString }}",
                    suggestions.any { it.lookupString == chosen })
                if (prefix == "llvm.") {
                    assertTrue(suggestions.any { it.lookupString == "add" })
                    assertTrue(suggestions.any { it.lookupString == "mul" })
                }
                myFixture.lookup.currentItem = suggestions.first { it.lookupString == chosen }
                myFixture.finishLookup('\n')
                assertEquals("module {\n  llvm.$chosen\n}\n", myFixture.editor.document.text)
            }
        }
    }

    fun testDisablingClearsExistingDiagnosticWithoutEditingAndReenableRestoresIt() {
        withServer("// LSP_TEST_ERROR\nmodule {}\n") {
            PlatformTestUtil.waitWithEventsDispatching("Server diagnostic did not reach the editor", {
                myFixture.doHighlighting().any { it.description == "MLIR LSP integration diagnostic" }
            }, 15)
            val markup = com.intellij.openapi.editor.impl.DocumentMarkupModel.forDocument(
                myFixture.editor.document, project, false)
            fun hasDiagnostic() = markup.allHighlighters.any {
                (it.errorStripeTooltip as? com.intellij.codeInsight.daemon.impl.HighlightInfo)
                    ?.description == "MLIR LSP integration diagnostic"
            }
            assertTrue(hasDiagnostic())
            val unrelated = markup.addRangeHighlighter(0, 1,
                com.intellij.openapi.editor.markup.HighlighterLayer.WARNING, null,
                com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE)
            try {
                val originalText = myFixture.editor.document.text
                val settings = project.service<MLIRLanguageServerSettings>()
                settings.state.enabled = false
                project.service<MLIRLanguageServerService>().restart()
                // Inspect actual markup: doHighlighting() would force a new highlighting pass.
                PlatformTestUtil.waitWithEventsDispatching("Disabled server left stale diagnostic markup", {
                    !hasDiagnostic()
                }, 15)
                assertTrue("Unrelated editor highlighting must remain", unrelated.isValid)
                assertEquals(originalText, myFixture.editor.document.text)
                settings.state.enabled = true
                project.service<MLIRLanguageServerService>().restart()
                PlatformTestUtil.waitWithEventsDispatching("Reenabled server did not restore diagnostics", {
                    hasDiagnostic()
                }, 15)
            } finally {
                markup.removeHighlighter(unrelated)
            }
        }
    }

    fun testOperationCompletionPrefersServerAndFallsBackForUnmatchedPrefix() {
        withServer("// LSP_TEST_ERROR\nmodule {}\n") {
            PlatformTestUtil.waitWithEventsDispatching("Document was not synchronized to the server", {
                myFixture.doHighlighting().any { it.description == "MLIR LSP integration diagnostic" }
            }, 15)
            val existing = "module {\n  llvm.add\n  llvm.fadd\n  llvm.fsub\n  "
            for (prefix in listOf("llvm.", "llvm.f")) {
                WriteCommandAction.runWriteCommandAction(project) {
                    myFixture.editor.document.setText("$existing$prefix\n}\n")
                }
                com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
                myFixture.editor.caretModel.moveToOffset(existing.length + prefix.length)
                val names = myFixture.completeBasic()!!.map { it.lookupString }
                if (prefix == "llvm.") {
                    assertTrue(names.toString(), names.containsAll(listOf("add", "sub", "mul")))
                    assertFalse(names.toString(), names.any { it.startsWith("llvm.") })
                } else {
                    assertTrue(names.toString(), names.containsAll(listOf("llvm.fadd", "llvm.fsub")))
                }
                com.intellij.codeInsight.lookup.LookupManager.getInstance(project).hideActiveLookup()
            }
        }
    }

    fun testOperationCompletionFallsBackWhenServerDisabled() {
        myFixture.configureByText("fallback.mlir",
            "module {\n  llvm.add\n  llvm.sub\n  llvm.<caret>\n}\n")
        val names = myFixture.completeBasic()!!.map { it.lookupString }
        assertTrue(names.toString(), names.containsAll(listOf("llvm.add", "llvm.sub")))
    }

    private fun withServer(text: String, check: () -> Unit) {
        assumeTrue("Protocol fixture requires Python 3", Files.isExecutable(Path.of("/usr/bin/python3")))
        val script = myFixture.tempDirFixture.createFile("server with spaces.py",
            javaClass.getResource("/lsp/server.py")!!.readText())
        val settings = project.service<MLIRLanguageServerSettings>()
        settings.loadState(MLIRLanguageServerSettings.Options(enabled = true,
            source = MLIRLanguageServerSettings.Source.EXECUTABLE, executable = "/usr/bin/python3",
            arguments = ParametersListUtil.join(script.path), workingDirectory = myFixture.tempDirFixture.tempDirPath))
        val lifecycle = project.service<MLIRLanguageServerService>()
        val manager = LspServerManager.getInstance(project)
        val wasTrusted = TrustedProjects.isProjectTrusted(project)
        TrustedProjects.setProjectTrusted(project, true)
        fun servers() = manager.getServersForProvider(MLIRLspServerSupportProvider::class.java)
        val file = myFixture.addFileToProject("diagnostic.mlir", text)
        val root = file.virtualFile.parent
        ModuleRootModificationUtil.updateModel(module) { it.addContentEntry(root) }
        try {
            lifecycle.restart()
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            myFixture.configureFromExistingVirtualFile(file.virtualFile)
            FileEditorManager.getInstance(project).openFile(file.virtualFile, true)
            assertTrue(TrustedProjects.isProjectTrusted(project))
            assertNotNull(com.intellij.openapi.extensions.ExtensionPointName
                .create<com.intellij.platform.lsp.api.LspServerSupportProvider>("com.intellij.platform.lsp.serverSupportProvider")
                .findExtension(MLIRLspServerSupportProvider::class.java))
            assertTrue(FileEditorManager.getInstance(project).openFiles.contains(file.virtualFile))
            manager.startServersIfNeeded(MLIRLspServerSupportProvider::class.java)
            PlatformTestUtil.waitWithEventsDispatching(java.util.function.Supplier {
                "MLIR server did not initialize: ${lifecycle.preparationStatus}; ${servers().map { it.state }}"
            }, {
                servers().any { it.state == LspServerState.Running }
            }, 15)
            assertEquals(1, servers().count { it.state == LspServerState.Running })
            check()
            settings.state.enabled = false
            lifecycle.restart()
            PlatformTestUtil.waitWithEventsDispatching("Server did not stop when disabled", {
                servers().none { it.state == LspServerState.Running || it.state == LspServerState.Initializing }
            }, 15)
        } finally {
            settings.loadState(MLIRLanguageServerSettings.Options())
            lifecycle.restart()
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            TrustedProjects.setProjectTrusted(project, wasTrusted)
            ModuleRootModificationUtil.updateModel(module) { model ->
                model.contentEntries.filter { it.file == root }.forEach { model.removeContentEntry(it) }
            }
        }
    }
}
