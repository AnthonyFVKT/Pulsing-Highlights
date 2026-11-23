plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "dev.anton"
version = "0.2.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // The plugin only uses platform APIs, so Community is enough for development.
        intellijIdeaCommunity("2024.1.7")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("241")
            untilBuild.set(provider { null })
        }
    }

    buildSearchableOptions.set(false)

    publishing {
        // Upload from CI/local: set PUBLISH_TOKEN to a permanent token from
        // https://plugins.jetbrains.com/author/me/tokens
        token.set(providers.environmentVariable("PUBLISH_TOKEN"))
        channels.set(listOf("default"))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}







