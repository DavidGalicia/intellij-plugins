import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
apply(from = "../contrib-configuration/common.gradle.kts")

// to run vuejs tests create a link to the vuejs gradle project here: ln -s ~/IdeaProjects/intellij-plugins/vuejs $ideaHomePath/vuejs
var ideaHomePath = "/home/user0/idea-IU-251.23774.435"

plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm") version "2.1.20"
  id("org.jetbrains.intellij.platform") version "2.5.0"
  id("org.jetbrains.intellij.platform.migration") version "2.5.0"
}

intellijPlatform {
  pluginConfiguration {
    name = "Vue.js-development"
  }
}

repositories {
  mavenCentral()
  maven("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public/")
  maven("https://download.jetbrains.com/teamcity-repository/")
  
  intellijPlatform {
    localPlatformArtifacts()
    defaultRepositories()
  }
}

dependencies {
  intellijPlatform {
    //intellijIdeaUltimate("LATEST-EAP-SNAPSHOT", useInstaller = false)
    local(ideaHomePath)
    testPlugins(listOf(
      "org.jetbrains.plugins.stylus:251.23774.318",
      "com.jetbrains.plugins.jade:251.23774.318",
    ))
    bundledPlugins(listOf(
      "JavaScript",
      "JavaScriptDebugger",
      "JSIntentionPowerPack",
      "com.intellij.css",
      "org.jetbrains.plugins.sass",
      "org.jetbrains.plugins.less",
      "intellij.webpack",
      "org.intellij.plugins.postcss",
      "intellij.prettierJS",
      "HtmlTools",
      "com.intellij.modules.json",
      "org.intellij.plugins.markdown"
    ))
    testFramework(TestFrameworkType.Platform)
  }
}

kotlin {
  kotlinDaemonJvmArgs = listOf("-Xmx4000m", "-XX:MaxMetaspaceSize=1000m")
}

tasks {
  compileKotlin {
    @Suppress("UNCHECKED_CAST")
    kotlinOptions.freeCompilerArgs = rootProject.extensions["kotlin.freeCompilerArgs"] as List<String>
  }
  wrapper {
    gradleVersion = ext("gradle.version")
  }
  runIde {
    //autoReloadPlugins.set(false)
  }
  test {
    useJUnit()
    jvmArgs("-Didea.home.path=$ideaHomePath")
  }
  jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  }
}

sourceSets {
  main {
    kotlin {
      setSrcDirs(listOf("src", "gen"))
    }
    resources {
      setSrcDirs(listOf("resources", "gen-resources"))
    }
  }
  test {
    kotlin {
      setSrcDirs(listOf("vuejs-tests/src"))
      exclude("**/VueFormatterTest.kt")
      exclude("**/VueModuleImportTest.kt")
      exclude("**/VueParameterInfoTest.kt")
      exclude("**/VueLoaderTest.kt")
      exclude("**/VueWebpackTest.kt")
      exclude("**/VueParserTest.kt")
      exclude("**/VueCopyrightTest.kt")
      exclude("**/LibrariesTestSuite.kt")
    }
  }
}

dependencies {
  implementation("org.apache.commons:commons-text:1.13.1")
  testImplementation("com.jetbrains.intellij.javascript:javascript-test-framework:251.23774.435")
  testImplementation("com.jetbrains.intellij.platform:web-symbols:251.23774.435")
  testImplementation("com.jetbrains.intellij.platform:web-symbols-test-framework:251.23774.435")
  testImplementation("junit:junit:4.13.2")
}

fun ext(name: String): String =
  rootProject.extensions[name] as? String
  ?: error("Property `$name` is not defined")