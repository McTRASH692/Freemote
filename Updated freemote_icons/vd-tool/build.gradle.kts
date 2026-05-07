import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import proguard.gradle.ProGuardTask

plugins {
    application
    alias(libs.plugins.shadowJar)
    `embedded-kotlin`
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.5.0")
    }
}
kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.android.ide.common.vectordrawable.VdCommandLineTool")
}

dependencies {
    implementation(libs.com.android.tools.sdkCommon) {
        exclude(group = "com.android.tools", module = "repository")
        exclude(group = "com.android.tools", module = "sdklib")
        exclude(group = "com.android.tools.ddms", module = "ddmlib")
        exclude(group = "com.android.tools.layoutlib", module = "layoutlib-api")
    }
    implementation(libs.com.android.tools.common)
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("vd-tool")
    archiveClassifier.set("")
    archiveVersion.set("")
}

tasks.register<ProGuardTask>("proguardJar") {
    outputs.upToDateWhen { false }
    dependsOn("clean")
    dependsOn("shadowJar")
    configuration("proguard-rules.pro")

    doLast {
        try {
            // Simulate ProGuard execution
            println("Running ProGuard...")
        } catch (e: Exception) {
            throw GradleException("ProGuard failed with error: ${e.message}", e)
        }
    }
}
