// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.utils.rootProjectPath
import java.nio.file.Files.createSymbolicLink

apply(from = "../contrib-configuration/common.gradle.kts")

intellijPlatform {
  pluginConfiguration {
    name = "Vue.js: Custom"
  }
}

repositories {
  maven("https://www.jetbrains.com/intellij-repository/releases/")
  maven("https://download.jetbrains.com/teamcity-repository")
  maven("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public/")

  intellijPlatform {
    localPlatformArtifacts()
    defaultRepositories()
  }
}

var ideaHomePath = "/home/user0/idea-IU-252.26199.169"

plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm") version "2.2.0"
  id("org.jetbrains.intellij.platform") version "2.9.0"
}

dependencies {
  intellijPlatform {
    //intellijIdeaUltimate("LATEST-EAP-SNAPSHOT", useInstaller = false)
    local(ideaHomePath)
    bundledPlugins(listOf(
      "JavaScript",
      "JSIntentionPowerPack",
      "JavaScriptDebugger",
      "com.intellij.css",
      "org.jetbrains.plugins.sass",
      "org.jetbrains.plugins.less",
      "intellij.webpack",
      "org.intellij.plugins.postcss",
      "intellij.prettierJS",
      "HtmlTools",
      "com.intellij.modules.json",
      "org.intellij.plugins.markdown",
      "com.intellij.copyright"
    ))
    testPlugins(listOf(
      "org.jetbrains.plugins.stylus:252.23892.298",
      "com.jetbrains.plugins.jade:252.23892.298"
    ))
    testFramework(TestFrameworkType.Platform)
    testFramework(TestFrameworkType.Plugin.JavaScript)
  }
}

tasks {
  compileKotlin {
    @Suppress("UNCHECKED_CAST")
    compilerOptions.freeCompilerArgs.set(rootProject.extensions["kotlin.freeCompilerArgs"] as List<String>)
  }
  compileTestKotlin {
    exclude("**/VueLoaderTest.kt")
    exclude("**/VueWebpackTest.kt")
    exclude("**/VueModuleImportTest.kt")
  }
  java {
    //sourceCompatibility = JavaVersion.toVersion(ext("java.sourceCompatibility"))
    //targetCompatibility = JavaVersion.toVersion(ext("java.targetCompatibility"))
  }
  wrapper {
    gradleVersion = ext("gradle.version")
  }
  runIde {
    intellijPlatform {
      autoReload = false
    }
  }

  test {
    systemProperty("idea.home.path", ideaHomePath)

    doFirst {
      // Create link to the intellij-plugins folder
      val contribDir = file("$ideaHomePath/contrib")
      if (!contribDir.exists()) {
        createSymbolicLink(contribDir.toPath(), rootProjectPath.parent)
      }

      // Create missing JavaScript Language Services directory
      val jsLanguageServicesLink = file("$ideaHomePath/plugins/JavaScriptLanguage/resources/jsLanguageServicesImpl")
      if (!jsLanguageServicesLink.exists()) {
        val jsLanguageResourcesDir = file("$ideaHomePath/plugins/JavaScriptLanguage/resources")
        jsLanguageResourcesDir.mkdirs()
        val jsLanguageServices = file("$ideaHomePath/plugins/javascript-plugin/jsLanguageServicesImpl")
        createSymbolicLink(jsLanguageServicesLink.toPath(), jsLanguageServices.toPath())
      }

      // Ensure JS test framework's mock Node types path exists to prevent NoSuchFileException in JSTestUtils
      val mockTypesNodeDir = file("$ideaHomePath/plugins/NodeJS/tests/testData/mockNode/node_modules/@types/node")
      if (!mockTypesNodeDir.exists()) {
        mockTypesNodeDir.mkdirs()
        // Provide a minimal type definition file so consumers can resolve the package
        val indexDts = mockTypesNodeDir.resolve("index.d.ts")
        if (!indexDts.exists()) {
          indexDts.writeText(
            """
            // Minimal stub for @types/node required by tests
            declare const __dummy: unknown;
            export = __dummy;
            """.trimIndent()
          )
        }
        // Also add a minimal package.json so the directory looks like a package
        val pkgJson = mockTypesNodeDir.resolve("package.json")
        if (!pkgJson.exists()) {
          pkgJson.writeText(
            """
            {
              "name": "@types/node",
              "version": "0.0.0-test-stub",
              "types": "index.d.ts"
            }
            """.trimIndent()
          )
        }
      }
    }

    exclude("**/VueLoaderTest.kt")
    exclude("**/VueWebpackTest.kt")
    exclude("**/VueModuleImportTest.kt")
  }

  // Copy directories into the plugin root in the sandbox and distribution
  prepareSandbox {
    from("typescript-vue-plugin") {
      into("${pluginName.get()}/typescript-vue-plugin")
    }
    from("vue-language-server") {
      into("${pluginName.get()}/vue-language-server")
    }
    from("vue-service") {
      into("${pluginName.get()}/vue-service")
    }
  }
  // Ensure test sandbox also contains the directories for local runs/tests
  prepareTestSandbox {
    from("typescript-vue-plugin") {
      into("${pluginName.get()}/typescript-vue-plugin")
    }
    from("vue-language-server") {
      into("${pluginName.get()}/vue-language-server")
    }
    from("vue-service") {
      into("${pluginName.get()}/vue-service")
    }
  }
}

sourceSets {
  main {
    java {
      setSrcDirs(listOf("src", "gen"))
    }
    kotlin {
      setSrcDirs(listOf("src", "gen"))
    }
    resources {
      setSrcDirs(listOf("resources", "gen-resources"))
    }
  }
  test {
    java {
      setSrcDirs(listOf("vuejs-tests/src"))
    }
    kotlin {
      setSrcDirs(listOf("vuejs-tests/src"))
    }
  }
}

dependencies {
  implementation("org.apache.commons:commons-text:1.14.0")
  testImplementation("com.jetbrains.intellij.platform:poly-symbols-test-framework:252.26199.169")
  testImplementation("junit:junit:${ext("junit.version")}")
  testImplementation("org.opentest4j:opentest4j:1.3.0")
}

fun ext(name: String): String =
  rootProject.extensions[name] as? String
    ?: error("Property `$name` is not defined")