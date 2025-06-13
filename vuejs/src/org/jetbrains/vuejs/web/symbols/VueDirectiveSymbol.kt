// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.vuejs.web.symbols

import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.query.PolySymbolsListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolsNameMatchQueryParams
import com.intellij.polySymbols.query.PolySymbolsScope
import com.intellij.util.containers.Stack
import org.jetbrains.vuejs.codeInsight.fromAsset
import org.jetbrains.vuejs.model.VueDirective
import org.jetbrains.vuejs.model.VueModelVisitor
import org.jetbrains.vuejs.web.VUE_DIRECTIVES
import org.jetbrains.vuejs.web.VUE_DIRECTIVE_ARGUMENT
import org.jetbrains.vuejs.web.VUE_DIRECTIVE_MODIFIERS
import org.jetbrains.vuejs.web.asPolySymbolPriority

open class VueDirectiveSymbol(
  name: String,
  directive: VueDirective,
  private val vueProximity: VueModelVisitor.Proximity,
) : VueScopeElementSymbol<VueDirective>(fromAsset(name), directive) {

  override val qualifiedKind: PolySymbolQualifiedKind
    get() = VUE_DIRECTIVES

  override val priority: PolySymbol.Priority
    get() = vueProximity.asPolySymbolPriority()

  override fun getMatchingSymbols(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolsNameMatchQueryParams,
    scope: Stack<PolySymbolsScope>,
  ): List<PolySymbol> =
    if (qualifiedName.matches(VUE_DIRECTIVE_ARGUMENT, VUE_DIRECTIVE_MODIFIERS)) {
      listOf(VueAnySymbol(this.origin, qualifiedName.qualifiedKind, qualifiedName.name))
    }
    else emptyList()

  override fun getSymbols(
    qualifiedKind: PolySymbolQualifiedKind,
    params: PolySymbolsListSymbolsQueryParams,
    scope: Stack<PolySymbolsScope>,
  ): List<PolySymbol> =
    if (qualifiedKind == VUE_DIRECTIVE_ARGUMENT) {
      listOf(VueAnySymbol(this.origin, qualifiedKind, "Vue directive argument"))
    }
    else emptyList()

  override fun createPointer(): Pointer<VueDirectiveSymbol> {
    val component = item.createPointer()
    val name = this.name
    val vueProximity = this.vueProximity
    return Pointer {
      component.dereference()?.let { VueDirectiveSymbol(name, it, vueProximity) }
    }
  }
}