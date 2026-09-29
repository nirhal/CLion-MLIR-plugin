package org.komlir.intellijmlirplugin.psi

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator

/** Keeps the source identifiable even after platform completion decorators are applied. */
internal class MLIROperationCompletionItem(delegate: LookupElement, val fromLanguageServer: Boolean) :
    LookupElementDecorator<LookupElement>(delegate)
