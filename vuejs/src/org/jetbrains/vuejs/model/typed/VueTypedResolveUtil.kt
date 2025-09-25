package org.jetbrains.vuejs.model.typed

import com.intellij.javascript.web.js.WebJSResolveUtil.resolveSymbolPropertiesFromAugmentations
import com.intellij.lang.ecmascript6.psi.ES6ExportDeclaration
import com.intellij.lang.ecmascript6.psi.ES6ImportExportDeclaration
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
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.vuejs.lang.html.isVueFileName
import kotlin.collections.forEach

fun resolveDtsFileFromDeclaration(declaration: ES6ImportExportDeclaration?): PsiFile? {
  val reference = declaration?.fromClause?.referenceText?.trim('"', '\'') ?: return null
  val directory = declaration.containingFile.containingDirectory.virtualFile
  val virtualFile = directory.findFileByRelativePath("$reference.d.ts")
    ?: directory.findFileByRelativePath("$reference.d.mts")
    ?: directory.findFileByRelativePath("$reference/index.d.ts")
    ?: directory.findFileByRelativePath("$reference/index.d.mts")

  return if (virtualFile != null) {
    PsiManager.getInstance(declaration.project).findFile(virtualFile)
  } else {
    null
  }
}

/**
 * Finds Vue components exported by a source element.
 *
 * Exports use the form: `export {default as Component} from './Component.vue'`
 */
fun find(element: PsiElement?, onVueTypedComponentFound: (component: VueTypedComponent) -> Unit) {
  if (element == null) {
    return
  }

  if (element is ES6ImportedBinding) {
    if (element.isNamespaceImport) {
      val file = resolveDtsFileFromDeclaration(element.declaration)
      return find(file, onVueTypedComponentFound)
    }
  }

  if (element is ES6ImportSpecifier) {
    val specifierName = element.name ?: return
    val file = resolveDtsFileFromDeclaration(element.declaration) ?: return
    val resolvedElement = ES6PsiUtil.resolveSymbolInModule(specifierName, file, file as JSFile).firstOrNull()?.element

    return find(resolvedElement, onVueTypedComponentFound)
  }

  if (element is ES6NamespaceExport) {
    val file = resolveDtsFileFromDeclaration(element.declaration)
    return find(file, onVueTypedComponentFound)
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
        return onVueTypedComponentFound(VueTypedComponent(specifier, symbol))
      }
      return
    }

    if (declaration.isExportAll) {
      val file = resolveDtsFileFromDeclaration(declaration)
      return find(file, onVueTypedComponentFound)
    }
  }

  if (element is TypeScriptTypeAlias) {
    if (element.typeDeclaration is TypeScriptMappedType) {
      val mappedType = element.typeDeclaration as TypeScriptMappedType
      return handleMappedTypeAlias(element, mappedType, onVueTypedComponentFound)
    }

    if (element.typeDeclaration is TypeScriptTypeOperator) {
      val op = element.typeDeclaration as TypeScriptTypeOperator
      val symbol = op.operatorType?.text
      if (symbol != null) {
        val result = ES6PsiUtil.resolveSymbolInModule(symbol, element.containingFile, element.containingFile as JSFile).firstOrNull()?.element
        return find(result, onVueTypedComponentFound)
      }
    }

    if (element.typeDeclaration is TypeScriptTypeofType) {
      val typeOf = element.typeDeclaration as TypeScriptTypeofType
      val symbol = typeOf.referenceText
      if (symbol != null) {
        val result = ES6PsiUtil.resolveSymbolInModule(symbol, element.containingFile, element.containingFile as JSFile).firstOrNull()?.element
        return find(result, onVueTypedComponentFound)
      }
    }

    return
  }

  if (element is JSFile) {
    for (child in element.children) {
      if (child is ES6ExportDeclaration) {
        find(child, onVueTypedComponentFound)
      }
    }
  }
}

/**
 * Handles mapped types of the form `[K in keyof Components]: Components[K]` and `[K in ComponentKeys]: Components[K]`
 */
fun handleMappedTypeAlias(alias: TypeScriptTypeAlias, mappedType: TypeScriptMappedType, onVueTypedComponentFound: (component: VueTypedComponent) -> Unit) {
  val keyIndex = mappedType.typeParameter?.text ?: return
  val keyIndexParts = keyIndex.split(" ")
  val propertiesIndex = mappedType.resultTypeElement?.text ?: return

  // Expect `K in ComponentKeys` or `K in keyof Components`
  if (!keyIndex.contains(" in ")) {
    return
  }

  // Expect `Components[K]`
  if (propertiesIndex.split("[").size != 2) {
    return
  }

  val properties = propertiesIndex.split("[")[0] // Components symbol
  val propertiesFile = ES6PsiUtil.resolveSymbolInModule(properties, alias.containingFile, alias.containingFile as JSFile).firstOrNull()?.element
  val propertyComponentMap = mutableMapOf<String, VueTypedComponent>()
  find(propertiesFile) {
    component -> propertyComponentMap[component.defaultName] = component
  }

  val key = keyIndexParts[0] // K

  // Expect `[K in keyof Components]: Components[K]`
  if (keyIndex.contains("$key in keyof $properties")) {
    if (propertiesIndex.replace(" ", "").contains("$properties[$key]")) {
      propertyComponentMap.forEach { (name, component) ->
        onVueTypedComponentFound(component)
      }
    }
  } else {
    // Expect `[K in ComponentKeys]: Components[K]`
    val keys = keyIndexParts[keyIndexParts.lastIndex] // ComponentKeys symbol
    val keyComponentMap = mutableMapOf<String, VueTypedComponent>()
    val keysFile = ES6PsiUtil.resolveSymbolInModule(keys, alias.containingFile, alias.containingFile as JSFile).firstOrNull()?.element
    find(keysFile) {
      component -> keyComponentMap[component.defaultName] = component
    }
    keyComponentMap.forEach { (name) ->
      val component = propertyComponentMap[name]
      if (component != null) {
        onVueTypedComponentFound(component)
      }
    }
  }
}

fun getTypeScriptDeclarationFiles(project: Project): List<JSFile> {
  val files = mutableListOf<JSFile>()
  val projectRootManager = ProjectRootManager.getInstance(project)

  for (root in projectRootManager.contentRoots) {
    VfsUtilCore.iterateChildrenRecursively(root, null) { file ->
      if (file.name.contains(".d.ts") || file.name.contains(".d.mts")) {
        val jsFile = PsiManager.getInstance(project).findFile(file) as? JSFile

        if (jsFile != null) {
          files.add(jsFile)
        }
      }

      true
    }
  }

  return files
}

/**
 * Finds exported Vue components used by a module and interface.
 *
 * Exports use the form: `export {default as Component} from './Component.vue'`
 */
fun findComponentsFromDtsES6ExportDeclarations(project: Project, tsModules: Set<String>, tsInterfaceName: String): Map<String, VueTypedComponent> {
  val components = mutableMapOf<String, VueTypedComponent>()
  val files = getTypeScriptDeclarationFiles(project)

  files.forEach { file ->
    for (element in file.children) {
      if (element is TypeScriptModule) {
        val module: TypeScriptModule = element
        var moduleName = module.name ?: continue
        val moduleNameComponents = moduleName.split(":")
        if (moduleNameComponents.size > 1) {
          moduleName = moduleNameComponents[1]
        }
        if (tsModules.contains(moduleName)) {
          module.children.forEach { element ->
            if ((element is TypeScriptInterface) && (element.name == tsInterfaceName)) {
              val tsInterface: TypeScriptInterface = element
              tsInterface.extendsList?.members?.forEach { member ->
                val symbol = member.referenceText
                if (symbol != null) {
                  // returns an element from the current file or a file referenced in an import declaration from the current file
                  val result = ES6PsiUtil.resolveSymbolInModule(symbol, file, file).first().element
                  find(result) { component ->
                    components[component.defaultName] = component
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  return components
}

/**
 * Project-level cached result for exported Vue components used by a module and interface.
 *
 * Exports use the form: `export {default as Component} from './Component.vue'`
 */
fun getCachedComponentsFromDtsES6ExportDeclarations(project: Project, tsModuleNames: Set<String>, tsInterfaceName: String): Map<String, VueTypedComponent> {
  val manager = CachedValuesManager.getManager(project)
  val key = com.intellij.openapi.util.Key.create<CachedValue<Map<String, VueTypedComponent>>>(
    "findComponentsFromDtsES6ExportDeclarations:" + tsModuleNames.sorted().joinToString(",") + ":" + tsInterfaceName
  )

  return manager.getCachedValue(project, key, {
      val result = findComponentsFromDtsES6ExportDeclarations(project, tsModuleNames, tsInterfaceName)
      CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT)
    },
    false
  )
}

fun resolveSymbolPropertiesFromAugmentations2(source: PsiElement, moduleNames: Set<String>, symbolName: String): Map<String, VueTypedComponent> {
  val base = getCachedComponentsFromDtsES6ExportDeclarations(source.project, moduleNames, symbolName)
  val augmentedProperties = resolveSymbolPropertiesFromAugmentations(source, moduleNames, symbolName)
  val result = buildMap {
    for ((name, component) in base) {
      put(name, component)
    }

    for ((name, source) in augmentedProperties) {
      put(name, VueTypedComponent(source, name))
    }
  }

  return result
}