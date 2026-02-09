val fabricLoaderVersion = findProperty("fabric_loader_version")?.toString() ?: "0.15.0"
val cobblemonVersion = findProperty("cobblemon_version")?.toString() ?: "1.6.0+1.21.1"
val reiVersion = findProperty("rei_version")?.toString() ?: "16.0.799"

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
}

tasks.processResources {
    inputs.property("version", project.version)
}
