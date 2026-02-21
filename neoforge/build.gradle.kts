val neoforgeVersion = findProperty("neoforge_version")?.toString() ?: "21.1.77"
val kotlinForForgeVersion = findProperty("kotlin_for_forge_version")?.toString() ?: "5.11.0"
val cobblemonVersion = findProperty("cobblemon_version")?.toString() ?: "1.6.0+1.21.1"
val reiVersion = findProperty("rei_version")?.toString() ?: "16.0.799"
val jeiVersion = findProperty("jei_version")?.toString() ?: "19.27.0.340"
val emiVersion = findProperty("emi_neoforge_version")?.toString() ?: "1.1.19+1.21.1"

plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

base {
    archivesName.set("cobbledex-rei-emi-jei-neoforge")
}

repositories {
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

architectury {
    platformSetupLoomIde()
    neoForge()
}

val common: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}
val shadowBundle: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

configurations {
    compileClasspath.get().extendsFrom(common)
    runtimeClasspath.get().extendsFrom(common)
    named("developmentNeoForge").get().extendsFrom(common)
}

dependencies {
    common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":common", configuration = "transformProductionNeoForge"))

    "neoForge"("net.neoforged:neoforge:$neoforgeVersion")
    implementation("thedarkcolour:kotlinforforge-neoforge:$kotlinForForgeVersion")

    modCompileOnly("com.cobblemon:neoforge:$cobblemonVersion")

    // REI NeoForge
    modCompileOnly("me.shedaniel:RoughlyEnoughItems-api-neoforge:$reiVersion")
    modRuntimeOnly("me.shedaniel:RoughlyEnoughItems-neoforge:$reiVersion")

    // JEI NeoForge
    modCompileOnly("mezz.jei:jei-1.21.1-neoforge-api:$jeiVersion")

    // EMI NeoForge (Modrinth maven)
    modCompileOnly("maven.modrinth:emi:$emiVersion")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier.set("dev-shadow")
}

tasks.remapJar {
    inputFile.set(tasks.shadowJar.get().archiveFile)
    archiveClassifier.set("")
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier.set("dev")
    from("LICENSE")
}
