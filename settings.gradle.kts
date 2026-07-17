pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // karoo-ext is published to GitHub Packages, which requires authentication
        // even for public packages. Provide a GitHub username + a classic PAT with
        // the `read:packages` scope via either:
        //   - ~/.gradle/gradle.properties: gpr.user=<user>  gpr.key=<token>
        //   - or env vars: USERNAME=<user>  TOKEN=<token>
        // See README ("Build prerequisites").
        maven {
            url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
            credentials {
                username = providers.gradleProperty("gpr.user").getOrElse(System.getenv("USERNAME"))
                password = providers.gradleProperty("gpr.key").getOrElse(System.getenv("TOKEN"))
            }
        }
    }
}

rootProject.name = "Quiver Karoo"
include("app")
