package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.ExecutionException
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.configurations.WrappingRunConfiguration
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jdom.Element

class MLIRFolderTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl()

    private fun configuration(path: String): MLIRRunConfiguration =
        MLIRRunConfiguration(project, MLIRRunConfigurationType().getFactory(), "MLIR folder").apply { file = path }

    fun testRecursiveDiscoverySkipsFilesWithoutRunAndPreservesErrors() {
        val root = myFixture.addFileToProject("tests/a test.mlir", "// RUN: tool %s\n// RUN: tool --second\n").virtualFile.parent
        myFixture.addFileToProject("tests/nested/a test.mlir", "// RUN: tool %s\n")
        myFixture.addFileToProject("tests/not-a-test.mlir", "module {}\n")
        myFixture.addFileToProject("tests/ignored.txt", "// RUN: tool\n")
        myFixture.addFileToProject("tests/bad.mlir", "// RUN: tool %unsupported\n")
        val tests = MLIRTestDiscovery.discover(project, root.path)
        assertEquals(listOf("a test.mlir", "bad.mlir", "nested/a test.mlir"), tests.map { it.name })
        assertEquals(2, tests.first().test!!.pipelines.size)
        assertEquals(listOf(root.path + "/a test.mlir"), tests.first().test!!.pipelines.first().commands.first().arguments)
        assertNotNull(tests[1].error)
        assertEquals(listOf("a test.mlir", "bad.mlir"), MLIRTestDiscovery.discover(project, root.path, false).map { it.name })
    }

    fun testExcludedDirectoriesAreNotDiscovered() {
        val root = myFixture.addFileToProject("tests/visible.mlir", "// RUN: tool\n").virtualFile.parent
        val excluded = myFixture.addFileToProject("tests/excluded/hidden.mlir", "// RUN: tool\n").virtualFile.parent
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.addContentEntry(root).addExcludeFolder(excluded)
        }
        try {
            assertEquals(listOf("visible.mlir"), MLIRTestDiscovery.discover(project, root.path).map { it.name })
        } finally {
            ModuleRootModificationUtil.updateModel(module) { model ->
                model.contentEntries.filter { it.file == root }.forEach { model.removeContentEntry(it) }
            }
        }
    }

    fun testEmptyFolderAndMissingRerunFile() {
        val root = myFixture.addFileToProject("empty/input.mlir", "module {}\n").virtualFile.parent
        assertEmpty(MLIRTestDiscovery.discover(project, root.path))
        val missing = root.path + "/deleted.mlir"
        val rerun = MLIRTestDiscovery.discover(project, root.path, selectedPaths = setOf(missing))
        assertEquals(listOf(missing), rerun.map { it.path })
        assertTrue(rerun.single().error!!.contains("no longer available"))
    }

    fun testSavedFileConfigurationAndFolderSettingsRoundTrip() {
        val file = myFixture.addFileToProject("legacy.mlir", "// RUN: tool\n").virtualFile
        val legacy = configuration(file.path)
        legacy.readExternal(Element("configuration").setAttribute("MLIR_FILE_PATH", file.path)
            .setAttribute("SHOW_ALL_PROCESSES_OUTPUT", "true"))
        assertEquals(file.path, legacy.file)
        assertFalse(legacy.isFolder)
        assertTrue(legacy.recursive)
        assertTrue(legacy.showAllProcessesOutput)
        assertTrue(legacy.updateSettings())
        val folder = configuration(file.parent.path).apply { recursive = false }
        val xml = Element("configuration")
        folder.writeExternal(xml)
        val restored = configuration("").apply { readExternal(xml) }
        assertEquals(folder.file, restored.file)
        assertTrue(restored.isFolder)
        assertFalse(restored.recursive)
        assertFalse((restored.clone() as MLIRRunConfiguration).recursive)
        assertEquals(1, restored.beforeRunTasks.count { it.providerId == MLIRBuildBeforeRunTaskProvider.ID })
    }

    fun testFolderDebugIsRejectedBeforeDiscovery() {
        val root = myFixture.addFileToProject("tests/a.mlir", "// RUN: tool\n").virtualFile.parent
        val config = configuration(root.path)
        val executor = DefaultDebugExecutor.getDebugExecutorInstance()
        val env = ExecutionEnvironmentBuilder.create(project, DefaultRunExecutor.getRunExecutorInstance(), config).build()
        try {
            config.getState(executor, env)
            fail("Folder debug must not silently debug the first file")
        } catch (e: ExecutionException) {
            assertTrue(e.message!!.contains("Folder debugging is not supported"))
        }
    }

    fun testRunStateDefersDiscoveryAndSharesPlanWithBeforeRunEnvironment() {
        val root = myFixture.addFileToProject("tests/input.mlir", "module {}\n").virtualFile.parent
        val config = configuration(root.path)
        val env = ExecutionEnvironmentBuilder.create(project, DefaultRunExecutor.getRunExecutorInstance(), config).build()
        // Creating the state for an empty folder must not scan the folder or report an error on the EDT.
        val state = env.state
        assertInstanceOf(state, MLIRBuildPlanOwner::class.java)
        val beforeRunEnv = ExecutionEnvironmentBuilder(env).build()
        assertSame(state, beforeRunEnv.getUserData(MLIRBuildPlanOwner.KEY))
    }

    fun testFolderResultsNavigateAndRerunOnlyFailedFiles() {
        val first = myFixture.addFileToProject("tests/a/same name.mlir", "// RUN: tool\n").virtualFile
        val second = myFixture.addFileToProject("tests/b/deep/same name.mlir", "// RUN: tool\n").virtualFile
        val root = first.parent.parent
        myFixture.addFileToProject("tests/same name.mlir", "// RUN: tool\n")
        myFixture.addFileToProject("tests/b/deep/another.mlir", "// RUN: tool\n")
        val tests = MLIRTestDiscovery.discover(project, root.path)
        val config = configuration(root.path)
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val properties = MLIRTestConsoleProperties(config, executor, tests)
        val console = properties.createTestConsole()
        Disposer.register(testRootDisposable, console)
        val handler = MLIRTestProcessHandler(tests, "tests") { test, _ ->
            if (test.path == second.path) throw java.io.IOException("Tool missing")
            object : com.intellij.execution.process.ProcessHandler() {
                override fun startNotify() { super.startNotify(); notifyProcessTerminated(0) }
                override fun destroyProcessImpl() = notifyProcessTerminated(130)
                override fun detachProcessImpl() = destroyProcessImpl()
                override fun detachIsDefault() = false
                override fun getProcessInput(): java.io.OutputStream? = null
            }
        }
        console.attachToProcess(handler)
        handler.startNotify()
        assertTrue(handler.waitFor(5000))
        val resultRoot = console.resultsViewer.testsRootNode
        PlatformTestUtil.waitWithEventsDispatching("Missing folder results", {
            resultRoot.children.singleOrNull()?.children?.size == 3 && !resultRoot.isInProgress
        }, 10)
        val results = resultRoot.children.single().children.associateBy { it.name }
        assertEquals(setOf("a", "b", "same name.mlir"), results.keys)
        assertTrue(results.getValue("same name.mlir").isLeaf)
        val firstResult = results.getValue("a").children.single()
        val deep = results.getValue("b").children.single()
        assertEquals("deep", deep.name)
        assertEquals(setOf("another.mlir", "same name.mlir"), deep.children.map { it.name }.toSet())
        val secondResult = deep.children.single { it.name == "same name.mlir" }
        assertEquals("same name.mlir", firstResult.name)
        assertTrue(firstResult.isPassed)
        assertTrue(secondResult.isDefect)
        assertEquals(first, firstResult.getLocation(project, GlobalSearchScope.allScope(project))?.virtualFile)
        assertEquals(second, secondResult.getLocation(project, GlobalSearchScope.allScope(project))?.virtualFile)
        val env = ExecutionEnvironmentBuilder.create(project, executor, config).build()
        val profile = properties.createRerunFailedTestsAction(console).getRunProfileTestAccessor(env)
            as WrappingRunConfiguration<*>
        val rerun = profile.peer as MLIRRunConfiguration
        assertEquals(setOf(second.path), rerun.selectedTestPaths)
        assertNull(config.selectedTestPaths)
        assertEquals(listOf(second.path), MLIRTestDiscovery.discover(project, rerun.file!!,
            rerun.recursive, rerun.selectedTestPaths).map { it.path })
        val failedPath = second.path
        com.intellij.openapi.application.WriteAction.run<RuntimeException> { second.delete(this) }
        val deletedProfile = properties.createRerunFailedTestsAction(console).getRunProfileTestAccessor(env)
            as WrappingRunConfiguration<*>
        assertEquals(setOf(failedPath), (deletedProfile.peer as MLIRRunConfiguration).selectedTestPaths)
    }
}
