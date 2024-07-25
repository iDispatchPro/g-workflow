import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.text.SimpleDateFormat
import java.util.*

plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.1.0"
    id("maven-publish")
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
}

dependencies {
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.6")
    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:1.9.23")

    implementation("k:lib:24.7.4.1858")
    implementation("k:lib-docker:24.7.9.1706")

    implementation("org.testng:testng:7.10.2")
}

afterEvaluate {
    (tasks["sourcesJar"] as Jar).duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val orangeColor = "\u001B[33m"
val blueColor = "\u001B[34m"
val resetColor = "\u001B[0m"

fun getProp(name : String) : String {
    val propsFile = file("gradle-local.properties")
    val gradleValue = providers.gradleProperty(name).getOrNull()
    val envName = "${project.name}_$name"

    val value = System.getenv(envName) ?: if (propsFile.exists())
        Properties().let {
            it.load(propsFile.inputStream())
            it.getProperty(name, gradleValue)
        }
    else
        gradleValue

    if (value == null)
        throw Exception("Environment var [$envName] or property [$name] not found in gradle-local.properties or gradle.properties")

    return value
}

group = "ru.oldscoolgeek"
val javaVersion = "21"

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = javaVersion
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

gradlePlugin {
    plugins {
        create("$group") {
            displayName = project.name
            id = "$group.${project.name.lowercase()}"
            implementationClass = project.name
            description = "Creates a standard build workflow for services."
            tags.set(listOf("build", "services"))
        }
    }
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = version
    }
}

tasks.register("g-deploy") {
    group = "[${project.name.lowercase()}]"
    version = SimpleDateFormat("yy.M.d.HHmm").format(Date())

    dependsOn("publish")

    doLast {
        println("\n${blueColor}Current ${project.name} version: $version\n")
        println("Please use this line for importing plugin:")
        println("""${orangeColor}id("${project.group}.${project.name.lowercase()}") version "$version"$resetColor""")
    }
}

repositories {
    maven {
        url = uri(getProp("mavenDependsURL"))

        credentials {
            username = getProp("mavenLogin")
            password = getProp("mavenPassword")
        }
    }
}

publishing {
    repositories {
        maven {
            url = uri(getProp("mavenPluginsURL"))

            credentials {
                username = getProp("mavenLogin")
                password = getProp("mavenPassword")
            }
        }
    }
}