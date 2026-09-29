package org.komlir.intellijmlirplugin.run_configuration

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil

/** Discovery does not require lit configuration or a CMake model. */
object MLIRTestDiscovery {
    data class TestCase(val path: String, val name: String, val test: RunCommandParser.TestFile?, val error: String? = null) {
        val locationHint: String get() = "file://$path:${test?.pipelines?.first()?.line ?: 1}"
    }

    fun discover(project: Project, path: String, recursive: Boolean = true, selectedPaths: Set<String>? = null): List<TestCase> =
        ReadAction.compute<List<TestCase>, RuntimeException> {
            val root = LocalFileSystem.getInstance().findFileByPath(path)
                ?: error("MLIR file or folder does not exist: $path")
            val files = mutableListOf<VirtualFile>()
            val index = ProjectFileIndex.getInstance(project)
            fun visit(file: VirtualFile) {
                ProgressManager.checkCanceled()
                if (index.isExcluded(file)) return
                if (file.isDirectory) {
                    // Do not follow directory symlinks outside the selection or into cycles.
                    if ((!file.`is`(VFileProperty.SYMLINK) || file == root) && (recursive || file == root)) {
                        file.children.forEach(::visit)
                    }
                } else if (file.extension == "mlir") files += file
            }
            if (selectedPaths == null) visit(root)
            else selectedPaths.sorted().forEach { selected ->
                val file = LocalFileSystem.getInstance().findFileByPath(selected)
                if (file != null && !file.isDirectory && !index.isExcluded(file) &&
                    (file == root || VfsUtilCore.isAncestor(root, file, false))) files += file
            }
            val found = files.sortedBy { it.path }.mapNotNull { file ->
                ProgressManager.checkCanceled()
                val psi = PsiManager.getInstance(project).findFile(file) ?: error("Cannot read MLIR file: ${file.path}")
                val hasRun = PsiTreeUtil.findChildrenOfType(psi, PsiComment::class.java)
                    .any { RunCommandParser.extractCommand(it) != null }
                if (!hasRun && root.isDirectory && selectedPaths == null) return@mapNotNull null
                val name = if (root.isDirectory) VfsUtilCore.getRelativePath(file, root)!! else file.name
                try {
                    TestCase(file.path, name, RunCommandParser.parse(psi, file.path))
                } catch (e: RunCommandParser.ParseException) {
                    TestCase(file.path, name, null, e.message)
                }
            }.toMutableList()
            // A deleted failed file must not cause a rerun to silently run other tests.
            selectedPaths?.filter { selected -> found.none { it.path == selected } }?.forEach {
                val name = if (root.isDirectory) it.removePrefix(root.path + "/") else it.substringAfterLast('/')
                found += TestCase(it, name, null, "Test file is no longer available: $it")
            }
            found
        }
}
