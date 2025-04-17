// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.vuejs.model.typed

import com.intellij.javascript.web.js.WebJSResolveUtil.resolveSymbolFromAugmentations
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.vuejs.index.GLOBAL_COMPONENTS
import org.jetbrains.vuejs.index.VUE_MODULE
import org.jetbrains.vuejs.model.*
import com.intellij.lang.ecmascript6.psi.ES6ExportDeclaration
import com.intellij.lang.ecmascript6.psi.ES6ImportSpecifier
import com.intellij.lang.ecmascript6.psi.ES6ImportedBinding
import com.intellij.lang.ecmascript6.psi.ES6NamespaceExport
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.ecma6.TypeScriptInterface
import com.intellij.lang.javascript.psi.ecma6.TypeScriptMappedType
import com.intellij.lang.javascript.psi.ecma6.TypeScriptModule
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeOperator
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeofType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.*
import org.jetbrains.vuejs.lang.html.isVueFileName

fun getTypeScriptDeclarationFiles(project: Project): List<PsiFile> {
  val files = mutableListOf<PsiFile>()
  val projectRootManager = ProjectRootManager.getInstance(project)

  for (root in projectRootManager.contentRoots) {
    VfsUtilCore.iterateChildrenRecursively(root, null) { file ->
      if (file.name.contains(".d.ts") || file.name.contains(".d.mts")) {
        val psiFile = PsiManager.getInstance(project).findFile(file)

        if (psiFile != null) {
          files.add(psiFile)
        }
      }

      true
    }
  }

  return files
}

/**
 * Finds Vue components exported by `element` using recursion and updates `components`
 */
fun updateBaseGlobalComponents(components: MutableMap<String, VueComponent>, element: PsiElement?) {
  if (element == null) {
    return
  }

  if (element is ES6ImportedBinding) {
    if (element.isNamespaceImport) {
      val jsFile = element.declaration?.fromClause?.resolveReferencedElements()?.first()
      return updateBaseGlobalComponents(components, jsFile)
    }
  }

  if (element is ES6ImportSpecifier) {
    val specifier: ES6ImportSpecifier = element
    val jsFile = ES6PsiUtil.resolveSymbolForSpecifier(specifier).first().element
    return updateBaseGlobalComponents(components, jsFile)
  }

  if (element is ES6NamespaceExport) {
    val export: ES6NamespaceExport = element
    val jsFile = export.declaration?.fromClause?.resolveReferencedElements()?.first()
    return updateBaseGlobalComponents(components, jsFile)
  }

  if (element is ES6ExportDeclaration) {
    val declaration: ES6ExportDeclaration = element
    val referenceText = declaration.fromClause?.referenceText?.trim('\"', '\'') ?: ""

    if (isVueFileName(referenceText)) {
      var specifier = declaration.exportSpecifiers.find { specifier -> specifier.isExportDefault }
      var symbol = specifier?.referenceName
      if (specifier == null) {
        specifier = declaration.exportSpecifiers.find { specifier -> specifier.referenceName == "default" }
        symbol = specifier?.alias?.name
      }

      if ((specifier != null) && (symbol != null)) {
        val component = VueTypedComponent(specifier, symbol)
        components[symbol] = component
      }

      return
    }

    if (declaration.isExportAll) {
      // check new file for export declarations
      val jsFile = declaration.fromClause?.resolveReferencedElements()?.first()
      return updateBaseGlobalComponents(components, jsFile)
    }
  }

  if (element is TypeScriptTypeAlias) {
    // search is faster if the GlobalComponents interface does not
    // extend from a mapped typed which requires the following code for parsing:
    if (element.typeDeclaration is TypeScriptMappedType) {
      val mappedType = element.typeDeclaration as TypeScriptMappedType
      val keyIndex = mappedType.typeParameter?.text ?: ""
      val keyIndexParts = keyIndex.split(" ")
      val propertyIndex = mappedType.resultTypeElement?.text ?: ""

      // propertyIndex format should be `Components[K]`
      if (propertyIndex.split("[").size != 2) {
        return
      }

      val property = propertyIndex.split("[")[0]

      val result = ES6PsiUtil.resolveSymbolInModule(property, element.containingFile, element.containingFile as JSFile).first().element
      val components2 = mutableMapOf<String, VueComponent>()
      updateBaseGlobalComponents(components2, result)

      // keyIndex format should be `K in Components` or `K in keyof Components`
      if (!((keyIndexParts.size == 3) || (keyIndexParts.size == 4))) {
        return
      }

      val keyLabel = keyIndexParts[0]

      if (propertyIndex.replace(" ", "").contains("$property[$keyLabel]")) {
        if (keyIndex.contains("keyof $property")) {
          for (key in components2.keys) {
            val component = components2[key]
            if (component != null) {
              components[key] = component
            }
          }
        } else {
          val keySymbol = keyIndexParts[keyIndexParts.lastIndex]
          val components3 = mutableMapOf<String, VueComponent>()
          val result = ES6PsiUtil.resolveSymbolInModule(keySymbol, element.containingFile, element.containingFile as JSFile).first().element
          updateBaseGlobalComponents(components3, result)
          for (key in components3.keys) {
            val component = components2[key]
            if (component != null) {
              components[key] = component
            }
          }
        }
      }

      return
    }

    if (element.typeDeclaration is TypeScriptTypeOperator) {
      val op = element.typeDeclaration as TypeScriptTypeOperator
      val symbol = op.operatorType?.text
      if (symbol != null) {
        // check if it resolves to an export declaration
        val result = ES6PsiUtil.resolveSymbolInModule(symbol, element.containingFile, element.containingFile as JSFile).first().element
        return updateBaseGlobalComponents(components, result)
      }
    }

    if (element.typeDeclaration is TypeScriptTypeofType) {
      val typeOf = element.typeDeclaration as TypeScriptTypeofType
      val symbol = typeOf.referenceText
      if (symbol != null) {
        // check if it resolves to an export declaration
        val result = ES6PsiUtil.resolveSymbolInModule(symbol, element.containingFile, element.containingFile as JSFile).first().element
        return updateBaseGlobalComponents(components, result)
      }
    }

    return
  }

  if (element is JSFile) {
    val jsFile: JSFile = element
    for (child in jsFile.children) {
      if (child is ES6ExportDeclaration) {
        updateBaseGlobalComponents(components, child)
      }
    }
  }
}

/**
 * Finds Vue components exported by `GlobalComponents` interface `extends` members
 */
fun findBaseGlobalComponents(project: Project): MutableMap<String, VueComponent> {
  val components = mutableMapOf<String, VueComponent>()
  val files = getTypeScriptDeclarationFiles(project)
  val vueModuleNames = listOf("module:@$VUE_MODULE/runtime-core", "module:@$VUE_MODULE/runtime-dom", "module:$VUE_MODULE")

  files.forEach { file ->
    for (element in file.children) {
      if ((element is TypeScriptModule) && vueModuleNames.contains(element.name)) {
        val vueModule = element
        vueModule.children.forEach { element ->
          if ((element is TypeScriptInterface) && (element.name == GLOBAL_COMPONENTS)) {
            val gcInterface = element
            gcInterface.extendsList?.members?.forEach { member ->
              val symbol = member.referenceText
              if (symbol != null) {
                // returns an element from the current file or a file referenced in an import declaration from the current file
                val result = ES6PsiUtil.resolveSymbolInModule(symbol, file, file as JSFile).first().element
                updateBaseGlobalComponents(components, result)
              }
            }
          }
        }
      }
    }
  }

  return components
}

data class VueTypedGlobal(
  override val delegate: VueGlobal,
  override val source: PsiElement,
) : VueDelegatedEntitiesContainer<VueGlobal>(),
    VueGlobal {

  private val typedGlobalComponents: Map<String, VueComponent> =
    CachedValuesManager.getCachedValue(source) {
      val allResults = findBaseGlobalComponents(project)

      val results = resolveSymbolFromAugmentations(source, VUE_MODULE, GLOBAL_COMPONENTS)
      results.forEach { result ->
        allResults[result.key] = VueTypedComponent(result.value, result.key)
      }

      CachedValueProvider.Result.create(allResults, PsiModificationTracker.MODIFICATION_COUNT)
    }

  override val components: Map<String, VueComponent>
    get() = delegate.components + typedGlobalComponents

  override val apps: List<VueApp>
    get() = delegate.apps

  override val plugins: List<VuePlugin>
    get() = delegate.plugins

  override val unregistered: VueEntitiesContainer
    get() = delegate.unregistered

  override val project: Project
    get() = delegate.project

  override val packageJsonUrl: String?
    get() = delegate.packageJsonUrl

  override fun getParents(scopeElement: VueScopeElement): List<VueEntitiesContainer> =
    delegate.getParents(scopeElement)

  override fun createPointer(): Pointer<out VueGlobal> {
    val delegatePtr = delegate.createPointer()
    val sourcePtr = source.createSmartPointer()
    return Pointer {
      val delegate = delegatePtr.dereference() ?: return@Pointer null
      val source = sourcePtr.dereference() ?: return@Pointer null
      VueTypedGlobal(delegate, source)
    }
  }

  override val parents: List<VueEntitiesContainer>
    get() = emptyList()
}