pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Picks fabric-loom (26.1+, unobfuscated) or fabric-loom-remap (1.21.x) per version
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        /**
         * Creates `versions/{project}-{loader}` nodes, each built by `build.{loader}.gradle.kts`.
         * [project] is the folder name, [version] the Minecraft version the node compiles against.
         */
        fun match(project: String, vararg loaders: String, version: String = project) {
            for (loader in loaders) version("$project-$loader", version).buildscript("build.$loader.gradle.kts")
        }

        // 1.21.1 is being ported: no item_model component and no render states there yet
        // match("1.21.1", "fabric", "neoforge")
        match("1.21.11", "fabric", "neoforge")
        match("26.1", "fabric", "neoforge", version = "26.1.2")
        match("26.2", "fabric", "neoforge")
        match("26.3", "fabric", "neoforge")
        vcsVersion = "26.3-fabric"
    }
}

rootProject.name = "FreeFPV"
