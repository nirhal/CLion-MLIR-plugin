package org.komlir.intellijmlirplugin.lsp

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.execution.ParametersListUtil
import java.nio.file.Files
import java.nio.file.Path

internal object MLIRLanguageServerCommand {
    fun resolvePath(value: String, base: String?): Path {
        val path = Path.of(value)
        if (path.isAbsolute) return path.normalize()
        if (base.isNullOrBlank()) throw ExecutionException("A project directory is required for relative paths.")
        return Path.of(base).resolve(path).normalize()
    }

    fun create(options: MLIRLanguageServerSettings.Options, base: String?, executable: String = options.executable): GeneralCommandLine {
        if (executable.isBlank()) throw ExecutionException("Select an MLIR language server executable.")
        val path = resolvePath(executable, base)
        if (!Files.isRegularFile(path) || !Files.isExecutable(path)) {
            throw ExecutionException("Language server is missing or is not executable: $path")
        }
        val directory = options.workingDirectory.takeIf { it.isNotBlank() }?.let { resolvePath(it, base) }
            ?: base?.let { Path.of(it) }
        if (directory != null && !Files.isDirectory(directory)) {
            throw ExecutionException("Language server working directory does not exist: $directory")
        }
        return GeneralCommandLine(path.toString())
            .withParameters(ParametersListUtil.parse(options.arguments, false, true))
            .withWorkDirectory(directory?.toFile())
            .withEnvironment(options.environment)
            .withParentEnvironmentType(if (options.passParentEnvironment) GeneralCommandLine.ParentEnvironmentType.CONSOLE
                else GeneralCommandLine.ParentEnvironmentType.NONE)
            .withCharset(Charsets.UTF_8)
    }
}
