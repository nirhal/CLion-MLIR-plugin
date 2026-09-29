package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.execution.ExecutionException
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.cidr.cpp.cmake.model.CMakeConfiguration
import com.jetbrains.cidr.cpp.cmake.model.CMakeGeneratorSpec
import com.jetbrains.cidr.cpp.cmake.model.CMakeTarget
import com.jetbrains.cidr.cpp.execution.CMakeBuildProfileExecutionTarget
import java.io.File

class MLIRCMakePlanTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl()

    private fun target(name: String, vararg profiles: String): CMakeTarget = CMakeTarget("project", name, "", profiles.mapIndexed { id, profile ->
        val dir = File("/tmp/mlir build/$profile")
        CMakeConfiguration(id, profile, "Debug", dir, dir, emptyMap(), emptyList(), emptyMap(), File(dir, name), dir,
            CMakeConfiguration.TargetType.EXECUTABLE, CMakeConfiguration.Generator(CMakeGeneratorSpec.NONE, true),
            CMakeConfiguration.MacroContext("", "", ""), false)
    })

    fun testSelectedProfileIsUsedForEveryToolWithoutFallback() {
        val tools = MLIRCMakeTools(listOf(target("opt", "Release", "Debug"), target("check", "Release")), "Debug")
        assertEquals("Debug", tools.resolve("opt", true)!!.configuration.name)
        assertNull(tools.resolve("external-check", false))
        try {
            tools.resolve("check", false)
            fail("Must not select Release when Debug was requested")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("profile 'Debug'"))
        }
        try {
            tools.resolve("missing", true)
            fail("Missing first-stage target must fail")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("No CMake target"))
        }
    }

    fun testBuildPlanDeduplicatesToolsAcrossFilesAndPipelineStages() {
        val first = myFixture.addFileToProject("tests/a.mlir", "// RUN: opt %s | check %s\n// RUN: other %s | check %s\n").virtualFile
        myFixture.addFileToProject("tests/nested/b.mlir", "// RUN: opt %s | check %s\n")
        val configuration = MLIRRunConfiguration(project, MLIRRunConfigurationType().getFactory(), "folder").apply { file = first.parent.path }
        val env = ExecutionEnvironmentBuilder.create(project, DefaultRunExecutor.getRunExecutorInstance(), configuration)
            .target(CMakeBuildProfileExecutionTarget("Debug", "Debug")).build()
        val plan = MLIRExecutionPlan.prepare(configuration, env, listOf(target("opt", "Debug"), target("other", "Debug"), target("check", "Debug")))
        assertEquals(2, plan.tests.size)
        assertTrue(plan.tests.all { it.error == null })
        assertEquals(3, plan.buildConfigurations.size)
        assertEquals(3, plan.launchers.size)
        assertTrue(plan.buildConfigurations.all { it.targetAndConfigurationData!!.configurationName == "Debug" })
        val launchers = plan.launchers.values.toList()
        assertEquals(first.parent.path, launchers.first().configuration.workingDirectory)
        assertEquals(first.parent.path + "/nested", launchers.last().configuration.workingDirectory)
    }

    fun testEmptySelectionHasClearErrorAndMissingTargetsBecomeFailedTests() {
        val input = myFixture.addFileToProject("tests/input.mlir", "module {}\n").virtualFile
        val configuration = MLIRRunConfiguration(project, MLIRRunConfigurationType().getFactory(), "folder").apply { file = input.parent.path }
        val env = ExecutionEnvironmentBuilder.create(project, DefaultRunExecutor.getRunExecutorInstance(), configuration).build()
        try {
            MLIRExecutionPlan.prepare(configuration, env, emptyList())
            fail("Empty selection must not pass")
        } catch (e: ExecutionException) {
            assertTrue(e.message!!.contains("No .mlir files with RUN directives"))
        }
        myFixture.addFileToProject("tests/missing.mlir", "// RUN: missing\n")
        val plan = MLIRExecutionPlan.prepare(configuration, env, emptyList())
        assertTrue(plan.tests.single().error!!.contains("No CMake target named 'missing'"))
        assertEmpty(plan.buildConfigurations)
    }
}
