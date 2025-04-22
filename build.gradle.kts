import java.text.SimpleDateFormat
import java.util.*

plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.2.1"
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
}

val projectGroup = "ru.old-school-geek"

val http4kVer = "5.32.4.0"
val projectName = project.name
val projectId = projectName.lowercase()

val orangeColor = "\u001B[33m"
val resetColor = "\u001B[0m"

group = projectGroup

dependencies {
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.6")
    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:2.0.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.jnr:jnr-unixsocket:0.38.22")

    implementation("org.http4k:http4k-core:$http4kVer")
    implementation("org.http4k:http4k-client-okhttp:$http4kVer")

    implementation("org.testng:testng:7.10.2")

    implementation("com.squareup.okio:okio:3.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("org.eclipse.jgit:org.eclipse.jgit:7.1.0.202411261347-r")
}

sourceSets {
    main {
        java.setSrcDirs(listOf("../k-lib-common/src", "../k-lib-docker/src", "../k-lib-git/src"))
    }
}
/*
kotlin {
    jvmToolchain(21)
}*/

fun getProp(name : String) : String
{
    val propsFile = file("gradle-local.properties")
    val gradleValue = providers.gradleProperty(name).getOrNull()
    val envName = "${projectName}_$name"

    val value = System.getenv(envName)
        ?: if (propsFile.exists())
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

afterEvaluate {
    (tasks["sourcesJar"] as Jar).duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

java {
    withJavadocJar()
    withSourcesJar()
}

gradlePlugin {
    plugins {
        create(projectGroup) {
            displayName = projectName
            id = "$projectGroup.$projectId"
            implementationClass = "GWorkFlow"
            description = "Creates a standard build workflow for services."
            tags.set(listOf("build", "services", "publish"))
        }
    }
}

tasks
    .jar {
        manifest {
            attributes["Implementation-Version"] = version
        }
    }

tasks
    .register("g-deploy") {
        group = "[$projectId]"
        version = SimpleDateFormat("yy.M.d.HHmm").format(Date())

        dependsOn("publish")

        doLast {
            println("Please use this line for importing plugin:")
            println("""${orangeColor}id("$projectGroup.$projectId") version "$version"$resetColor""")
        }
    }

repositories {
    mavenLocal()

    /* maven {
         url = uri(getProp("mavenDependsURL"))

         credentials {
             username = getProp("mavenLogin")
             password = getProp("mavenPassword")
         }
     }*/

    mavenCentral()
}

publishing {
    repositories {
        /* maven {
             url = uri(getProp("mavenPluginsURL"))

             credentials {
                 username = getProp("mavenLogin")
                 password = getProp("mavenPassword")
             }
         }*/

        mavenLocal()
    }
}