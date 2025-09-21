repositories {
  mavenCentral()
  maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

rootProject.extensions.add("platform.version", "252.26199.169")

rootProject.extensions.add("gradle.version", "9.0")

rootProject.extensions.add("junit.version", "4.13.2")

rootProject.extensions.add("kotlin.jvmTarget", "21")
rootProject.extensions.add("kotlin.freeCompilerArgs", listOf("-Xjvm-default=all"))

rootProject.extensions.add("java.sourceCompatibility", "21")
rootProject.extensions.add("java.targetCompatibility", "21")