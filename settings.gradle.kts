rootProject.name = "ForcesOfGravium"

plugins {
    // See documentation on https://scaffoldit.dev
    id("dev.scaffoldit") version "0.2.+"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Would you like to do a split project?
// Create a folder named "common", then configure details with `common { }`

hytale {
    usePatchline("release")
    useVersion("latest")

    repositories {
        // Any external repositories besides: MavenLocal, MavenCentral, HytaleMaven, and CurseMaven
    }

    dependencies {
        // Any external dependency you also want to include
    }

    manifest {
        Group = "Moxinat"
        Name = "ForcesOfGravium"
        Main = "dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin"
    }
}
