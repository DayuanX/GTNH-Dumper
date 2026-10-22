import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask
import java.util.regex.Pattern

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// workaround an OOM on GH actions during the checkStyleMain task
tasks.withType<Checkstyle>().configureEach {
    maxHeapSize = "1g"
}

tasks.test.configure {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

fun parseGtVersion(versionStr: String): Triple<Int, Int, Int> {
    val components = versionStr.split(Pattern.compile("[.-]"), 5)
    val vMajor = 500 + components[1].toInt()
    val vMinor = components[2].toInt()
    val vPatch = components[3].toInt()
    return Triple(vMajor, vMinor, vPatch)
}

var gtNbtVersion: Triple<Int, Int, Int> = Triple(509, 44, 0)
var logLevel = LogLevel.INFO
try {
    val verStr = project.version.toString()
    gtNbtVersion = parseGtVersion(verStr)
} catch (_: Exception) {
    try {
        val verStr = providers.exec {
            commandLine("git", "describe")
        }.standardOutput.asText.get()
        gtNbtVersion = parseGtVersion(verStr)
        logLevel = LogLevel.LIFECYCLE
    } catch (_: Exception) {
        logger.error("Cannot automatically determine NBT version. Using defaults hardcoded in buildscript. This could break your world!")
        logLevel = LogLevel.WARN
    }
}

minecraft {
    injectedTags.put("VERSION_MAJOR", gtNbtVersion.first)
    injectedTags.put("VERSION_MINOR", gtNbtVersion.second)
    injectedTags.put("VERSION_PATCH", gtNbtVersion.third)
    logger.log(logLevel, "Using {} as NBT version", gtNbtVersion)
}

val functionalTest by sourceSets.creating {
    java {
        srcDir("src/functionalTest/java")
        compileClasspath += sourceSets.patchedMc.get().output + sourceSets.main.get().output
    }
}

configurations {
    // Keep all dependencies from the main mod in the functional test mod
    named(functionalTest.compileClasspathConfigurationName).configure {extendsFrom(configurations.compileClasspath.get())}
    named(functionalTest.runtimeClasspathConfigurationName).configure {extendsFrom(configurations.runtimeClasspath.get())}
    named(functionalTest.annotationProcessorConfigurationName).configure {extendsFrom(configurations.annotationProcessor.get())}
}

tasks.register<Jar>(functionalTest.jarTaskName) {
    archiveClassifier.set("functionalTests")
    // we don't care about the version number here, keep it stable to avoid polluting the tmp directory
    archiveVersion.set("1.0")
    destinationDirectory.set(layout.buildDirectory.dir("tmp"))
}

tasks.assemble.configure {
    dependsOn(functionalTest.jarTaskName)
}

val checkModFilter by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks mod filter matching and original recipe source semantics without launching Minecraft."
    classpath(functionalTest.runtimeClasspath, sourceSets.main.get().output, sourceSets.patchedMc.get().output)
    mainClass.set("com.iouter.gtnhdumper.ModFilterRegression")
}

tasks.check.configure {
    dependsOn(checkModFilter)
}

// Run tests in the default runServer/runClient configurations
tasks.named<RunMinecraftTask>("runServer").configure {
    dependsOn(functionalTest.jarTaskName)
    classpath(configurations.named(functionalTest.runtimeClasspathConfigurationName), tasks.named(functionalTest.jarTaskName))
}

tasks.named<RunMinecraftTask>("runClient").configure {
    dependsOn(functionalTest.jarTaskName)
    classpath(configurations.named(functionalTest.runtimeClasspathConfigurationName), tasks.named(functionalTest.jarTaskName))
}

val checkRecipeItemVariants by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks recipe item copies, count normalization and structural NBT identity."
    classpath(functionalTest.runtimeClasspath, sourceSets.main.get().output, sourceSets.patchedMc.get().output)
    mainClass.set("com.iouter.gtnhdumper.RecipeItemVariantsRegression")
}

tasks.check.configure {
    dependsOn(checkRecipeItemVariants)
}

val checkCraftingGrid by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks shaped crafting coordinates, empty cells and input-order invariance."
    classpath(functionalTest.runtimeClasspath, sourceSets.main.get().output, sourceSets.patchedMc.get().output)
    mainClass.set("com.iouter.gtnhdumper.CraftingGridRegression")
}

tasks.check.configure {
    dependsOn(checkCraftingGrid)
}

val checkExportWorldCleanup by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks export world cleanup isolation and redirected path rejection without launching Minecraft."
    classpath(functionalTest.runtimeClasspath, sourceSets.main.get().output, sourceSets.patchedMc.get().output)
    mainClass.set("com.iouter.gtnhdumper.automation.ExportWorldCleanupRegression")
}

tasks.check.configure {
    dependsOn(checkExportWorldCleanup)
}

val checkTimedIconFrames by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks icon render profiles and timed frame dimensions and ordering without launching Minecraft."
    classpath(configurations.compileClasspath, functionalTest.runtimeClasspath, sourceSets.main.get().output, sourceSets.patchedMc.get().output)
    mainClass.set("com.iouter.gtnhdumper.common.dumper.TimedIconFramesRegression")
}

tasks.check.configure { dependsOn(checkTimedIconFrames) }

val checkExportSettings by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks first-launch export configuration and restoration of temporary client settings."
    classpath(configurations.compileClasspath, functionalTest.runtimeClasspath, sourceSets.main.get().output, sourceSets.patchedMc.get().output)
    mainClass.set("com.iouter.gtnhdumper.automation.ExportSettingsRegression")
}

tasks.check.configure { dependsOn(checkExportSettings) }
