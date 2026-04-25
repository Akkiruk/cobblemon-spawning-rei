val fabricLoaderVersion = findProperty("fabric_loader_version")?.toString() ?: "0.15.0"
val cobblemonVersion = findProperty("cobblemon_version")?.toString() ?: "1.6.0+1.21.1"
val reiVersion = findProperty("rei_version")?.toString() ?: "16.0.799"
val jeiVersion = findProperty("jei_version")?.toString() ?: "19.27.0.340"
val emiVersion = findProperty("emi_version")?.toString() ?: "1.1.12+1.21"

architectury {
    common("fabric", "neoforge")
}

dependencies {
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modCompileOnly("com.cobblemon:mod:$cobblemonVersion")
    compileOnly("dev.architectury:architectury-injectables:1.0.10")

    // REI API (common, cross-platform)
    modCompileOnly("me.shedaniel:RoughlyEnoughItems-api:$reiVersion")
    modCompileOnly("me.shedaniel:RoughlyEnoughItems-default-plugin:$reiVersion")

    // JEI API (common)
    modCompileOnly("mezz.jei:jei-1.21.1-common-api:$jeiVersion")

    // EMI API (Modrinth maven)
    modCompileOnly("maven.modrinth:emi:$emiVersion")

    testImplementation(kotlin("test-junit5"))
}

tasks.processResources {
    inputs.property("version", project.version)
}

tasks.test {
    useJUnitPlatform()
}
