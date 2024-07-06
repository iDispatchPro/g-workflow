import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import k.common.*
import k.docker.models.Image
import k.marshalling.unMarshall
import org.gradle.api.*
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.*
import org.gradle.kotlin.dsl.*
import tasks.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

const val defaultGroup = "ru.wildberries"
const val GLOBAL_PREFIX = "j"
const val taskGroupMain = "[jam-main]"
const val taskGroupMore = "[jam-utils]"
const val jamInstancesLabel = "$taskGroupMain.jam"
const val GRADLE_HOME_VAR = "GRADLE_USER_HOME"
const val gradlePropsFile = "gradle.properties"
const val localPropsFile = "gradle-local.properties"
const val VERSION_FILE = "version.txt"

val deployName = "$GLOBAL_PREFIX-deploy"
val testName = "$GLOBAL_PREFIX-test"
val publishName = "$GLOBAL_PREFIX-publish"
val buildName = "$GLOBAL_PREFIX-build"
val checkName = "$GLOBAL_PREFIX-check"
val imagesName = "$GLOBAL_PREFIX-images"
val envUpName = "$GLOBAL_PREFIX-env-up"
val envDownName = "$GLOBAL_PREFIX-env-down"
val removeImages = "$GLOBAL_PREFIX-remove-images"
val removeVolumes = "$GLOBAL_PREFIX-remove-volumes"
val cleanName = "$GLOBAL_PREFIX-clean"
val runName = "$GLOBAL_PREFIX-run"
val devFinishName = "$GLOBAL_PREFIX-dev-finish"

val toReleaseName = "$GLOBAL_PREFIX-release"

val releaseMajorName = "$GLOBAL_PREFIX-release-major"
val releaseMinorName = "$GLOBAL_PREFIX-release-minor"
val releasePatchName = "$GLOBAL_PREFIX-release-patch"

val updateDependsName = "$GLOBAL_PREFIX-update-depends"

lateinit var jarName : String
lateinit var fullJarName : String
lateinit var productVer : String
lateinit var projectName : String
lateinit var buildDir : String
lateinit var projectDir : String
lateinit var versionFile : File
lateinit var dateStr : String

var isMainBranch : Boolean = false
val params : Parameters = (env("${System.getenv(GRADLE_HOME_VAR)}/$gradlePropsFile") + env(gradlePropsFile) + env(localPropsFile)).unMarshall<Parameters>()

val defaultDockerFile
    get() = File(buildDir, dockerFile)

fun isLib() =
    mainFiles.isEmpty()

class Jam : Plugin<Project> {
    private lateinit var project : Project

    private inline fun <reified T : Task> createTask(name : String, groupName : String = taskGroupMain) =
        project.tasks.create(name, T::class.java) { group = groupName }

    override fun apply(project : Project) {
        val branch = Git.getBranch()

        isMainBranch = branch in listOf("main", "master", "prod", "")

        fun calcVars() {
            this.project = project
            buildDir = project.layout.buildDirectory.get().str
            projectDir = project.projectDir.str
            projectName = project.name
            versionFile = File(projectDir, VERSION_FILE)
            dateStr = SimpleDateFormat("yy.M.d.HHmm").format(Date())

            productVer = if (versionFile.exists())
                versionFile.readText().trim()
            else
                dateStr

            jarName = "${project.name.lowercase()}.$productVer.jar"
            fullJarName = "$buildDir/$jarName"
        }

        fun configureProject() {
            project.version = productVer
            project.group = params.group

            project
                .repositories {
                    maven {
                        url = project.projectDir.resolve("depends").toURI()
                    }

                    maven {
                        url = params.mavenDependsURL

                        credentials {
                            username = params.mavenLogin
                            password = params.mavenPassword
                        }
                    }

                    mavenCentral()
                    gradlePluginPortal()
                }

            project.plugins.apply("org.jetbrains.kotlin.jvm")
            project.plugins.apply("java")
            project.plugins.apply("io.gitlab.arturbosch.detekt")

            //project.dependencies.add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit5:1.9.10")
            project.dependencies.add("implementation", "org.testng:testng:7.9.0")

            // fix "java.lang.module.ResolutionException: Modules jetty.servlet.api and jakarta.servlet export package jakarta.servlet.descriptor to module org.testng"
            project.dependencies.add("implementation", "org.eclipse.jetty.toolchain:jetty-servlet-api:4.0.6")

            val java = project.extensions.findByType(JavaPluginExtension::class.java) mustBeFound "JavaPluginExtension"

            if (isLib()) {
                java.withSourcesJar()

                project.plugins.apply("maven-publish")

                val javaSources = project.extensions.getByType(SourceSetContainer::class.java)

                File("src")
                    .listFiles()
                    ?.let {
                        javaSources["main"].java.srcDirs(it.filter { it.isDirectory && it.name != "test" })
                    }

                javaSources["test"].java.srcDirs(listOf("src/test/kotlin"))

                project.extensions.configure<PublishingExtension>("publishing") {
                    publications {
                        create<MavenPublication>(projectName) {
                            from(project.components["java"])

                            groupId = project.group.str
                            artifactId = projectName
                            version = productVer
                        }
                    }

                    repositories {
                        maven {
                            url = params.mavenPluginsURL

                            credentials {
                                username = params.mavenLogin
                                password = params.mavenPassword
                            }
                        }
                    }
                }
            }
            else
                project.plugins.apply("application")

            project.extensions.getByType(DetektExtension::class.java).apply {
                allRules = true
                parallel = true
                buildUponDefaultConfig = true
                ignoreFailures = true

                config.setFrom("$buildDir/$DETEKT_CONFIG")
            }

            project.extensions.getByType<JavaToolchainService>().launcherFor {
                languageVersion.set(JavaLanguageVersion.of(params.jdkVer))
            }

            java.toolchain.languageVersion.set(JavaLanguageVersion.of(params.jdkVer))

            project.gradle.startParameter.maxWorkerCount = 8
            project.gradle.startParameter.isParallelProjectExecutionEnabled = true
        }

        fun createTasks() {
            createTask<Check>(checkName)
            createTask<Clean>(cleanName)
            createTask<RemoveImages>(removeImages)
            createTask<RemoveVolumes>(removeVolumes)
            createTask<PrepareEnv>(envUpName)
            createTask<ShutdownEnv>(envDownName)

            project.tasks.create<DependencyUpdatesTask>(updateDependsName) {
                group = taskGroupMore

                rejectVersionIf {
                    isNonStable(candidate.version) && !isNonStable(currentVersion)
                }
            }

            project.tasks.create("$GLOBAL_PREFIX-get-app-version") {
                group = taskGroupMore

                doLast {
                    println(project.version)
                }
            }

            project.tasks.create("$GLOBAL_PREFIX-get-app-name") {
                group = taskGroupMore

                doLast {
                    println(project.name)
                }
            }

            if (isLib()) {
                createTask<DeployLib>(deployName)
                createTask<LibDevFinish>(devFinishName)
                createTask<LibTests>(testName)
            }
            else {
                project.tasks.create<Build>(buildName) {
                    group = taskGroupMain

                    // Этот блок перенесен сюда из метода Build.init, из-за ошибки
                    // Cannot change dependencies of dependency configuration ':implementation' after it has been included in dependency resolution.
                    // возникающей вследствие очередного припадка Gradle при обработке секции dependencies/implementation в проекте использующем текущий плагин
                    // Из-за переноса, понадобился костыль в классе Build (Помечен как "Костыль для корректного определение UP-TO-DATE").

                    doFirst {
                        val sources = project.extensions.getByType(SourceSetContainer::class.java)["main"]

                        from(sources.output + sources.runtimeClasspath.filter { it.exists() }.map { if (it.isDirectory) it else project.zipTree(it) })
                    }
                }

                createTask<Deploy>(deployName)
                createTask<Publish>(publishName)

                if (versionFile.exists())
                    createTask<ToRelease>(toReleaseName)
                else {
                    createTask<ReleaseMajor>(releaseMajorName)
                    createTask<ReleaseMinor>(releaseMinorName)
                    createTask<ReleasePatch>(releasePatchName)
                }

                createTask<Images>(imagesName)
                createTask<Run>(runName)
                createTask<DevFinish>(devFinishName)
                createTask<Tests>(testName)
            }
        }

        calcVars()
        Git.installHooks()
        configureProject()
        createTasks()
    }

    fun isNonStable(version : String) : Boolean {
        val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
        val regex = "^[0-9,.v-]+(-r)?$".toRegex()
        val isStable = stableKeyword || regex.matches(version)

        return isStable.not()
    }
}

val String.patched
    get() = this
        .replace("j-a-r", jarName)
        .replace("v-e-r-s-i-o-n", productVer)
        .replace("a-c-c-o-u-n-t", params.registryUrl.str)
        .replace("m-a-i-n--i-m-a-g-e", Image(params.registryUrl, projectName, productVer).fullName)

fun checkMainBranch() =
    isMainBranch orThrow "The task can only be run in the Main branch"