repositories {
    // Any external repositories besides: MavenLocal, MavenCentral, HytaleMaven, and CurseMaven
}

val hytaleServerVersion = "0.5.0"

configurations.configureEach {
    resolutionStrategy.force("com.hypixel.hytale:Server:$hytaleServerVersion")
}

dependencies {
    // Add external mod dependencies here when needed.
    constraints {
        implementation("com.hypixel.hytale:Server:$hytaleServerVersion") {
            because("Must match the locally installed Hytale assets version.")
        }
    }

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
