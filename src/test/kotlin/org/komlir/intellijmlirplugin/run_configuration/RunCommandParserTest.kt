package org.komlir.intellijmlirplugin.run_configuration

import junit.framework.TestCase

class RunCommandParserTest : TestCase() {
    private fun parse(vararg lines: String, path: String = "/tmp/test file.mlir") =
        RunCommandParser.parseComments(lines.mapIndexed { i, line -> i + 1 to "// $line" }, path)

    fun testArgumentsQuotesAndFilenameSubstitution() {
        val pipeline = parse("RUN: mlir-opt %s --pass='a b|c' \"\" | FileCheck \"%s\" --check-prefix=TEST").pipelines.single()
        assertEquals(listOf("/tmp/test file.mlir", "--pass=a b|c", ""), pipeline.commands[0].arguments)
        assertEquals("FileCheck", pipeline.commands[1].executable)
        assertEquals(listOf("/tmp/test file.mlir", "--check-prefix=TEST"), pipeline.commands[1].arguments)
    }

    fun testContinuedAndMultipleDirectives() {
        val test = parse("RUN: mlir-opt %s \\", "RUN: --canonicalize | FileCheck %s", "RUN: mlir-opt %s --verify-each")
        assertEquals(2, test.pipelines.size)
        assertEquals(1, test.pipelines[0].line)
        assertEquals(3, test.pipelines[1].line)
        assertEquals(listOf("/tmp/test file.mlir", "--canonicalize"), test.pipelines[0].commands[0].arguments)
    }

    fun testLiteralPipeEscapesAndPercent() {
        val command = parse("RUN: tool a\\ b x\\|y '%%name' \"quoted\\\"value\"").pipelines.single().commands.single()
        assertEquals(listOf("a b", "x|y", "%name", "quoted\"value"), command.arguments)
    }

    fun testSubstitutionDoesNotRetokenizeFilename() {
        val path = "/tmp/a' b|c\"d.mlir"
        assertEquals(listOf(path, "--input=$path"), parse("RUN: tool '%s' --input=%s", path = path)
            .pipelines.single().commands.single().arguments)
    }

    fun testRejectsUnsupportedOrMalformedCommands() {
        for (command in listOf("", "tool |", "| tool", "tool || other", "tool && other", "tool > out",
            "tool %t", "not tool", "env FOO=1 tool", "FOO=1 tool", "tool 'unterminated", "tool *.mlir",
            "tool $(pwd)")) {
            try { parse("RUN: $command"); fail("Accepted: $command") }
            catch (e: RunCommandParser.ParseException) { assertTrue(e.message!!.startsWith("Line 1:")) }
        }
    }

    fun testRejectsUnfinishedContinuationAndConditionalTests() {
        for (lines in listOf(arrayOf("RUN: tool \\"), arrayOf("RUN: tool", "XFAIL: *"),
            arrayOf("REQUIRES: gpu", "RUN: tool"), arrayOf("RUN: tool \\", "comment", "RUN: other"))) {
            try { parse(*lines); fail("Accepted ${lines.toList()}") }
            catch (_: RunCommandParser.ParseException) { }
        }
    }
}
