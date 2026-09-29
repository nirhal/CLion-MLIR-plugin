package org.komlir.intellijmlirplugin.lsp

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.components.service
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.xmlb.XmlSerializer
import java.nio.file.Path

class MLIRLanguageServerTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl()

    override fun setUp() {
        super.setUp()
        project.service<MLIRLanguageServerSettings>().loadState(MLIRLanguageServerSettings.Options())
        project.service<MLIRLanguageServerService>().restart()
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    override fun tearDown() {
        try { project.service<MLIRLanguageServerSettings>().loadState(MLIRLanguageServerSettings.Options()) }
        finally { super.tearDown() }
    }

    fun testCMakePreparationHasCoroutineJobAndPropagatesCancellation() {
        val support = object : MLIRLanguageServerCMakeSupport {
            override fun targets(project: com.intellij.openapi.project.Project) = emptyList<MLIRLanguageServerCMakeSupport.Target>()
            override fun subscribe(project: com.intellij.openapi.project.Project,
                                   parent: com.intellij.openapi.Disposable, changed: () -> Unit) = Unit
            override fun prepare(project: com.intellij.openapi.project.Project,
                                 options: MLIRLanguageServerSettings.Options, build: Boolean,
                                 indicator: com.intellij.openapi.progress.ProgressIndicator): GeneralCommandLine {
                // CidrBuild.execute uses this bridge, which rejects a context without a Job.
                com.intellij.openapi.progress.blockingContextToIndicator {
                    assertNotNull(com.intellij.openapi.progress.ProgressManager.getInstance().progressIndicator)
                }
                if (build) {
                    indicator.cancel()
                    indicator.checkCanceled()
                }
                return GeneralCommandLine("mlir-lsp-server")
            }
        }
        // Reproduce preparation from a legacy indicator task, without a parent coroutine Job.
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            for (cancel in listOf(false, true)) {
                val indicator = com.intellij.openapi.progress.EmptyProgressIndicator()
                var cancelled = false
                try {
                    com.intellij.openapi.progress.ProgressManager.getInstance().runProcess({
                        val command = support.prepareWithContext(project, MLIRLanguageServerSettings.Options(), cancel, indicator)
                        assertEquals("mlir-lsp-server", command.exePath)
                    }, indicator)
                } catch (_: com.intellij.openapi.progress.ProcessCanceledException) {
                    cancelled = true
                }
                assertEquals(cancel, cancelled)
            }
        }.get(10, java.util.concurrent.TimeUnit.SECONDS)
    }

    fun testSettingsRoundTripAndEnvironmentIsolation() {
        val options = MLIRLanguageServerSettings.Options(enabled = true, source = MLIRLanguageServerSettings.Source.CMAKE,
            target = "my-lsp", profile = "Debug", arguments = "--log=verbose", buildBeforeStart = true,
            environment = linkedMapOf("MLIR_TEST" to "value with spaces"), passParentEnvironment = false)
        val settings = project.service<MLIRLanguageServerSettings>()
        settings.loadState(options)
        options.environment["MLIR_TEST"] = "changed"
        assertEquals("value with spaces", settings.state.environment["MLIR_TEST"])
        val restored = XmlSerializer.deserialize(XmlSerializer.serialize(settings.state), MLIRLanguageServerSettings.Options::class.java)
        assertEquals(settings.state, restored)
        assertFalse(MLIRLanguageServerSettings().state.enabled)
    }

    fun testCommandKeepsSpacesAndExplicitEnvironment() {
        val executable = myFixture.tempDirFixture.createFile("tools with spaces/my lsp")
        val executablePath = Path.of(executable.path)
        assertTrue(executablePath.toFile().setExecutable(true))
        val options = MLIRLanguageServerSettings.Options(executable = "tools with spaces/my lsp",
            arguments = "--label 'two words' --flag", workingDirectory = "tools with spaces",
            environment = linkedMapOf("TEST_LSP" to "some value"), passParentEnvironment = false)
        val command = MLIRLanguageServerCommand.create(options, myFixture.tempDirFixture.tempDirPath)
        assertEquals(executable.path, command.exePath)
        assertEquals(listOf("--label", "two words", "--flag"), command.parametersList.list)
        assertEquals(executablePath.parent.toFile(), command.workDirectory)
        assertEquals("some value", command.environment["TEST_LSP"])
        assertEquals(GeneralCommandLine.ParentEnvironmentType.NONE, command.parentEnvironmentType)
        val descriptor = MLIRLspServerDescriptor(project, command)
        descriptor.createCommandLine().withEnvironment("MUTATED", "yes")
        assertFalse(descriptor.createCommandLine().environment.containsKey("MUTATED"))
    }

    fun testMissingExecutableAndInvalidWorkingDirectoryHaveActionableErrors() {
        fun rejected(options: MLIRLanguageServerSettings.Options, message: String) {
            try {
                MLIRLanguageServerCommand.create(options, myFixture.tempDirFixture.tempDirPath)
                fail("Expected invalid settings to fail")
            } catch (e: ExecutionException) { assertTrue(e.message!!, e.message!!.contains(message)) }
        }
        rejected(MLIRLanguageServerSettings.Options(executable = "missing"), "not executable")
        val executable = myFixture.tempDirFixture.createFile("server")
        Path.of(executable.path).toFile().setExecutable(true)
        rejected(MLIRLanguageServerSettings.Options(executable = executable.path, workingDirectory = "missing-directory"), "working directory")
    }

    fun testProviderDoesNotStartWhenDisabledOrForOtherLanguages() {
        val provider = MLIRLspServerSupportProvider()
        val starter = object : LspServerSupportProvider.LspServerStarter {
            override fun ensureServerStarted(descriptor: com.intellij.platform.lsp.api.LspServerDescriptor) {
                fail("A disabled server must not start")
            }
        }
        provider.fileOpened(project, myFixture.addFileToProject("test.mlir", "module {}").virtualFile, starter)
        project.service<MLIRLanguageServerSettings>().state.enabled = true
        provider.fileOpened(project, myFixture.addFileToProject("test.txt", "text").virtualFile, starter)
        assertEquals("Disabled", project.service<MLIRLanguageServerService>().preparationStatus)
    }

    fun testSettingsUiWorksWithoutOptionalCMakePlugin() {
        val configurable = MLIRLanguageServerConfigurable(project)
        try {
            assertNotNull(configurable.createComponent())
            assertFalse(configurable.isModified)
            configurable.apply()
            assertFalse(project.service<MLIRLanguageServerSettings>().state.enabled)
        } finally { configurable.disposeUIResources() }
    }

    fun testPreparationReusesDescriptorAndDisablingPreventsStarts() {
        val executable = myFixture.tempDirFixture.createFile("prepared-server")
        Path.of(executable.path).toFile().setExecutable(true)
        project.service<MLIRLanguageServerSettings>().loadState(MLIRLanguageServerSettings.Options(
            enabled = true, executable = executable.path, workingDirectory = myFixture.tempDirFixture.tempDirPath))
        val service = project.service<MLIRLanguageServerService>()
        val descriptors = mutableListOf<LspServerDescriptor>()
        val starter = object : LspServerSupportProvider.LspServerStarter {
            override fun ensureServerStarted(descriptor: LspServerDescriptor) { descriptors += descriptor }
        }
        // Backgroundable tasks execute synchronously in this test fixture.
        service.fileOpened(starter)
        assertEquals("Ready", service.preparationStatus)
        service.fileOpened(starter)
        service.fileOpened(starter)
        assertEquals(2, descriptors.size)
        assertSame(descriptors[0], descriptors[1])
        assertEquals(executable.path, descriptors[0].createCommandLine().exePath)
        project.service<MLIRLanguageServerSettings>().state.enabled = false
        service.fileOpened(starter)
        assertEquals(2, descriptors.size)
    }
}
