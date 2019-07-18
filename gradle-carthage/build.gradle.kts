plugins {
    id("org.jetbrains.kotlin.jvm")
    id("java-gradle-plugin")
}

repositories {
    jcenter()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.3.41")

    compileOnly(gradleApi())
    implementation("com.google.code.gson:gson:2.8.5")
    implementation("com.squareup.okhttp3:okhttp:4.0.1")

    testCompileOnly(gradleTestKit())
}
