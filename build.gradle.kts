/* This is free and unencumbered software released into the public domain */

import com.github.jengelman.gradle.plugins.shadow.internal.RelocationUtil
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.io.BufferedReader
import org.gradle.kotlin.dsl.provideDelegate

/* ------------------------------ Plugins ------------------------------ */
plugins {
    id("java") // Import Java plugin.
    id("java-library") // Import Java Library plugin.
    id("com.diffplug.spotless") version "8.1.0" // Import Spotless plugin.
    id("com.gradleup.shadow") version "8.3.9" // Import Shadow plugin.
    eclipse // Import Eclipse plugin.
    kotlin("jvm") version "2.1.21" // Import Kotlin JVM plugin.
}

extra["kotlinAttribute"] = Attribute.of("kotlin-tag", Boolean::class.javaObjectType)

val kotlinAttribute: Attribute<Boolean> by rootProject.extra

/* ---------------------------- Java / Kotlin -------------------------- */
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.GRAAL_VM
    }
}

kotlin { jvmToolchain(17) }

/* ----------------------------- Metadata ------------------------------ */
val commitHash =
    Runtime.getRuntime().exec(arrayOf("git", "rev-parse", "--short=10", "HEAD")).let { process ->
        process.waitFor()
        val output = process.inputStream.use { it.bufferedReader().use(BufferedReader::readText) }
        process.destroy()
        output.trim()
    }

group = "net.trueog.questsOG" // Declare bundle identifier.

val apiVersion = "1.19" // Declare minecraft server target version.

version = "$apiVersion-$commitHash" // Declare plugin version (will be in .jar).

/* ----------------------------- Resources ----------------------------- */
tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to version, "apiVersion" to apiVersion)
    inputs.properties(props) // Indicates to rerun if version changes.
    filesMatching("plugin.yml") { expand(props) }
    from("LICENSE") { into("/") } // Bundle licenses into jarfiles.
}

/* ---------------------------- Repos ---------------------------------- */
repositories {
    mavenCentral() // Import the Maven Central Maven Repository.
    gradlePluginPortal() // Import the Gradle Plugin Portal Maven Repository.
    maven { url = uri("https://repo.purpurmc.org/snapshots") } // Import the PurpurMC Maven Repository.
    maven { url = uri("https://jitpack.io") } // Import JitPack (NpcApi).
    maven { url = uri("file://${System.getProperty("user.home")}/.m2/repository") }
    System.getProperty("SELF_MAVEN_LOCAL_REPO")?.let { // TrueOG Bootstrap mavenLocal().
        val dir = file(it)
        if (dir.isDirectory) {
            println("Using SELF_MAVEN_LOCAL_REPO at: $it")
            maven { url = uri("file://${dir.absolutePath}") }
        } else {
            logger.error("TrueOG Bootstrap not found, defaulting to ~/.m2 for mavenLocal()")
            mavenLocal()
        }
    } ?: logger.error("TrueOG Bootstrap not found, defaulting to ~/.m2 for mavenLocal()")
}

/* ---------------------- Java project deps ---------------------------- */
// NpcApi is kept off runtimeClasspath so RelocationUtil never sees it; see relocateNpcApi below.
val npcApi: Configuration by configurations.creating { isTransitive = false }

val npcApiVersion = "2.3.3" // Last NpcApi release built for Java 17 (supports 1.19.4 via v1_19_R3).

dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT") // Declare Purpur API version to be packaged.
    compileOnly("net.luckperms:api:5.5") // Import the LuckPerms API.
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib") // Provided by DiamondBank-OG's shaded Kotlin runtime.
    compileOnly(
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2"
    ) // Provided by DiamondBank-OG's shaded coroutine runtime.
    implementation("io.lettuce:lettuce-core:7.2.0.RELEASE") // Import Lettuce API for keydb.
    implementation("org.slf4j:slf4j-nop:2.0.17") // Provide SLF4J NOP provider to suppress missing-provider warning.
    implementation("org.slf4j:slf4j-api:2.0.17") // Bundle a relocated SLF4J API for shaded dependencies.
    implementation("org.slf4j:slf4j-nop:2.0.17") // Provide a relocated no-op SLF4J backend to avoid provider warnings.
    compileOnly("com.github.Realizedd.Duels:duels-api:3.5.1") // Import Duels API (API-compatible with Duels-OG).
    compileOnlyApi(project(":libs:Utilities-OG")) // Import TrueOG Network Utilities-OG Java API (from source).
    compileOnlyApi(project(":libs:DiamondBank-OG")) {
        attributes { attribute(kotlinAttribute, true) }
    } // Import TrueOG network DiamondBank-OG Kotlin API (from source).
    implementation(project(":libs:GxUI-OG")) // Shade TrueOG Network GxUI-OG progress menu API into this plugin.
    compileOnly("com.github.Eisi05:NpcApi:$npcApiVersion") // Compile against NpcApi (relocated at shade time).
    npcApi("com.github.Eisi05:NpcApi:$npcApiVersion") // Shade NpcApi player NPC library into this plugin.
}

configurations.runtimeClasspath {
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "org.jetbrains.kotlinx")
}

/* ---------------------- Reproducible jars ---------------------------- */
tasks.withType<AbstractArchiveTask>().configureEach { // Ensure reproducible .jars
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

/* ----------------------------- Shadow -------------------------------- */
// NpcApi injects a netty handler into the server's player pipeline, so it must keep
// referencing the server's io.netty. Lettuce ships its own netty that the main shadowJar
// relocates under the shadow prefix. Stage 1 renames NpcApi's netty references to a
// placeholder package that the main task maps straight back to io.netty.
val relocateNpcApi by
    tasks.registering(ShadowJar::class) {
        archiveClassifier.set("npcapi-relocated")
        configurations = listOf(npcApi)
        relocate("io.netty", "questsog.nettyshim")
        exclude("META-INF/**")
        exclude("de/eisi05/npc/api/utils/Metrics*.class") // Drop bStats; a no-op stub ships in src/main/kotlin.
    }

tasks.shadowJar {
    dependsOn(relocateNpcApi)
    from(zipTree(relocateNpcApi.flatMap { it.archiveFile }))
    archiveClassifier.set("") // Use empty string instead of null.
    isEnableRelocation = false
    relocationPrefix = "${project.group}.shadow"
    relocate("kotlin", "net.trueog.diamondbankog.shadow.kotlin")
    relocate("kotlinx", "net.trueog.diamondbankog.shadow.kotlinx")
    relocate("questsog.nettyshim", "io.netty") // Map NpcApi's placeholder back onto the server's netty.
    relocate("de.eisi05", "${project.group}.shadow.de.eisi05") // Relocate NpcApi (and our call sites).
    doFirst { RelocationUtil.configureRelocation(this@shadowJar, relocationPrefix) }
    mergeServiceFiles()
    minimize { exclude(dependency("org.slf4j:slf4j-nop:.*")) }
}

tasks.jar { archiveClassifier.set("part") } // Applies to root jarfile only.

tasks.build { dependsOn(tasks.spotlessApply, tasks.shadowJar) } // Build depends on spotless and shadow.

/* --------------------------- Javac opts ------------------------------- */
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation") // Trigger deprecation warning messages.
    options.encoding = "UTF-8" // Use UTF-8 file encoding.
}

/* ----------------------------- Auto Formatting ------------------------ */
spotless {
    kotlin { ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) } }
    kotlinGradle {
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) } // JetBrains Kotlin formatting.
        target("build.gradle.kts", "settings.gradle.kts") // Gradle files to format.
    }
}

tasks.named("spotlessCheck") {
    dependsOn("spotlessApply") // Run spotless before checking if spotless ran.
}
