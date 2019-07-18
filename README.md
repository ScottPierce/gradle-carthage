### Example Usage:
```Kotlin
apply plugin: 'org.jetbrains.kotlin.multiplatform'

kotlin {
    iosArm32 {
        binaries {
            framework {
            }
        }
    }

    iosArm64 {
        binaries {
            framework {
            }
        }
    }

    iosX64 {
        binaries {
            framework {
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation "org.jetbrains.kotlin:kotlin-stdlib-common:$kotlin_version"
            }
        }

        iosX64Main {
            kotlin.srcDirs += project.file("src/iosMain/kotlin")
        }

        iosArm32Main {
            kotlin.srcDirs += project.file("src/iosMain/kotlin")
        }

        iosArm64Main {
            kotlin.srcDirs += project.file("src/iosMain/kotlin")
        }
    }

    task buildFatReleaseFramework(type: org.jetbrains.kotlin.gradle.tasks.FatFrameworkTask) {
        // The fat framework must have the same base name as the initial frameworks.
        baseName = project.name

        // The default destination directory is '<build directory>/fat-framework'.
        destinationDir = file("$buildDir/fat-framework/release")

        // Specify the frameworks to be merged.
        from(
                targets.iosArm32.binaries.getFramework("RELEASE"),
                targets.iosArm64.binaries.getFramework("RELEASE"),
                targets.iosX64.binaries.getFramework("RELEASE")
        )
    }
}

// This repository provides this gradle task, that you can configure and run:
task releaseFatReleaseFramework(type: dev.scottpierce.kotlin.carthage.GenerateCarthageReleaseTask) {
    versionString = "0.1.0"
    previousJsonFile = rootProject.file("./carthage/releases.json")
    baseReleaseUrl = // TODO - The base URL where your zip files will be hosted.
    frameworks = [
            file("${project.buildDir}/fat-framework/release/${project.name}.framework")
            // Put more framework directories here if you want to release more than one
    ]
    outputDir = rootProject.file("./carthage/")
}
```
