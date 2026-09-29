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
        assumeTrue("Protocol fixture requires Python 3", Files.isExecutable(Path.of("/usr/bin/python3")))
        val script = myFixture.tempDirFixture.createFile("server with spaces.py",
            javaClass.getResource("/lsp/server.py")!!.readText())
        val settings = project.service<MLIRLanguageServerSettings>()
        settings.loadState(MLIRLanguageServerSettings.Options(enabled = true, executable = "/usr/bin/python3",
            arguments = ParametersListUtil.join(script.path), workingDirectory = myFixture.tempDirFixture.tempDirPath))
        val lifecycle = project.service<MLIRLanguageServerService>()
        val manager = LspServerManager.getInstance(project)
        val wasTrusted = TrustedProjects.isProjectTrusted(project)
        TrustedProjects.setProjectTrusted(project, true)
        fun servers() = manager.getServersForProvider(MLIRLspServerSupportProvider::class.java)
        val file = myFixture.addFileToProject("diagnostic.mlir", "// LSP_TEST_ERROR\nmodule {}\n")
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
            PlatformTestUtil.waitWithEventsDispatching("Server diagnostic did not reach the editor", {
                myFixture.doHighlighting().any { it.description == "MLIR LSP integration diagnostic" }
            }, 15)
            WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText("module {}\n") }
            PlatformTestUtil.waitWithEventsDispatching("Diagnostic was not cleared after an unsaved edit", {
                myFixture.doHighlighting().none { it.description == "MLIR LSP integration diagnostic" }
            }, 15)
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
