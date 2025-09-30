// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.vuejs.model.typed

import com.intellij.lang.javascript.psi.JSRecordType
import com.intellij.lang.javascript.psi.JSType
import com.intellij.lang.javascript.psi.JSTypeOwner
import com.intellij.lang.javascript.psi.ecma6.*
import com.intellij.lang.javascript.psi.types.*
import com.intellij.lang.javascript.psi.types.evaluable.JSApplyNewType
import com.intellij.model.Pointer
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.asSafely
import org.jetbrains.vuejs.codeInsight.resolveElementTo
import org.jetbrains.vuejs.lang.html.isVueFileName
import org.jetbrains.vuejs.model.VueModelManager
import org.jetbrains.vuejs.model.VueRegularComponent
import com.intellij.lang.ecmascript6.psi.ES6ExportDefaultAssignment
import com.intellij.lang.ecmascript6.psi.ES6ExportSpecifier
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.psi.PsiFile

class VueTypedComponent(
  override val source: PsiElement,
  override val defaultName: String,
) : VueTypedContainer(source), VueRegularComponent {

  override val nameElement: PsiElement?
    get() = null

  private fun resolveDefaultComponentConstructorFromDtsFile(file: PsiFile?): JSType? {
    val jsFile = file as? JSFile ?: return null
    val export = ES6PsiUtil.resolveSymbolInModule("default", jsFile, jsFile).first().element

    if (export is ES6ExportDefaultAssignment) {
      val exportedVar = export.children.first()

      if (exportedVar is JSReferenceExpression) {
        val componentSymbol = exportedVar.text
        val component = ES6PsiUtil.resolveSymbolInModule(componentSymbol, jsFile, jsFile).first().element
        if (component is TypeScriptVariable) {
          val jsType = component.jsType ?: return null

          return JSApplyNewType(jsType, jsType.source).substitute()
        }
      }
    }

    return null
  }

  private fun resolveFromES6ExportSpecifier(source: ES6ExportSpecifier): JSType? {
    if (source.declaration?.fromClause?.referenceText?.contains(".vue") == true) {
      val declaration = source.declaration ?: return null
      val file = resolveDtsFileFromDeclaration(declaration)
      return resolveDefaultComponentConstructorFromDtsFile(file)
    }

    return null
  }

  override val thisType: JSType
    get() = CachedValuesManager.getCachedValue(source) {
      if (source is ES6ExportSpecifier) {
        val resolved = resolveFromES6ExportSpecifier(source)
        return@getCachedValue CachedValueProvider.Result.create(resolved, PsiModificationTracker.MODIFICATION_COUNT)
      }

      return@getCachedValue CachedValueProvider.Result.create(
        resolveElementTo(source, TypeScriptVariable::class, TypeScriptPropertySignature::class, TypeScriptClass::class)
          ?.let { componentDefinition ->
            when (componentDefinition) {
              is JSTypeOwner ->
                componentDefinition.jsType
                  ?.let { getFromVueFile(it) ?: JSApplyNewType(it, it.source).substitute() }
              is TypeScriptClass ->
                componentDefinition.jsType
              else -> null
            }
          },
        PsiModificationTracker.MODIFICATION_COUNT)
    } ?: JSAnyType.getWithLanguage(JSTypeSource.SourceLanguage.TS)

  private fun getFromVueFile(type: JSType): JSRecordType? {
    if (type is TypeScriptIndexedAccessJSTypeImpl
        && type.parameterType.let { it is JSStringLiteralTypeImpl && it.literal == "default" }) {
      val importType = type.owner as? JSImportType ?: return null
      val prefix = "typeof import("
      val contextFile = type.source.scope ?: return null
      return importType.qualifiedName.name
        .takeIf { it.startsWith(prefix) && it.endsWith(")") }
        ?.let { it.substring(prefix.length + 1, it.length - 2) }
        ?.takeIf { isVueFileName(it) }
        ?.let { contextFile.virtualFile?.parent?.findFileByRelativePath(it) }
        ?.let { contextFile.manager.findFile(it) }
        ?.let { VueModelManager.findEnclosingContainer(it) as? VueRegularComponent }
        ?.thisType
        ?.asRecordType()
    }
    return null
  }

  override val typeParameters: List<TypeScriptTypeParameter>
    get() = resolveElementTo(source, TypeScriptVariable::class, TypeScriptPropertySignature::class, TypeScriptClass::class)
              .asSafely<TypeScriptTypeParameterListOwner>()
              ?.typeParameters?.toList()
            ?: emptyList()

  override fun createPointer(): Pointer<out VueRegularComponent> {
    val sourcePtr = source.createSmartPointer()
    val defaultName = this.defaultName
    return Pointer {
      val source = sourcePtr.dereference() ?: return@Pointer null
      VueTypedComponent(source, defaultName)
    }
  }

  override fun equals(other: Any?): Boolean =
    other === this ||
    other is VueTypedComponent
    && other.source == this.source

  override fun hashCode(): Int =
    source.hashCode()

}