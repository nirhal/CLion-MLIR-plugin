package org.komlir.intellijmlirplugin

import com.intellij.lang.Language

object MLIRLanguage : Language("MLIR") {
    override fun getDisplayName(): String = "MLIR"
    override fun isCaseSensitive(): Boolean = true

    @JvmStatic
    private fun readResolve(): Any = MLIRLanguage
}
