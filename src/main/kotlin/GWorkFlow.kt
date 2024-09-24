import Extension.Companion.toExtension
import io.gitlab.arturbosch.detekt.DetektPlugin
import k.common.*
import k.docker.models.Image
import k.serializing.deSerialize
import org.gradle.api.*
import org.gradle.api.plugins.*
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.*
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.signing.SigningPlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import tasks.*
import tasks.version.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

const val pluginName = "G-Workflow"
val instancesLabel = pluginName.low
const val GLOBAL_PREFIX = "g"
const val taskGroupMain = "[$GLOBAL_PREFIX-main]"
const val taskGroupMore = "[$GLOBAL_PREFIX-utils]"
const val GRADLE_HOME_VAR = "GRADLE_USER_HOME"
const val gradlePropsFile = "gradle.properties"
const val localPropsFile = "gradle-local.properties"
val myPropsFile = "$instancesLabel.properties"
const val VERSION_FILE = "version.txt"
const val envDir = "env"
const val dependencyDir = ".dependencies"

val publishName = "$GLOBAL_PREFIX-publish"
val buildName = "$GLOBAL_PREFIX-build"
val checkName = "$GLOBAL_PREFIX-check"
val imagesName = "$GLOBAL_PREFIX-images"
val envUpName = "$GLOBAL_PREFIX-env-up"
val envDownName = "$GLOBAL_PREFIX-env-down"
val removeImages = "$GLOBAL_PREFIX-remove-images"
val cleanName = "$GLOBAL_PREFIX-clean"
val runName = "$GLOBAL_PREFIX-run"
val devFinishName = "$GLOBAL_PREFIX-dev-finish"

val toReleaseName = "$GLOBAL_PREFIX-release"

val releaseMajorName = "$GLOBAL_PREFIX-release-major"
val releaseMinorName = "$GLOBAL_PREFIX-release-minor"
val releasePatchName = "$GLOBAL_PREFIX-release-patch"

lateinit var jarName : String
lateinit var fullJarName : String
lateinit var productVer : String
lateinit var projectName : String
lateinit var buildDir : String
lateinit var projectDir : String
lateinit var versionFile : File
lateinit var dateStr : String
lateinit var extension : Extension

var isMainBranch : Boolean = false

val params : Parameters = (env("${System.getenv(GRADLE_HOME_VAR)}/$gradlePropsFile")
        + env(gradlePropsFile)
        + env(localPropsFile)
        + env(myPropsFile)).deSerialize<Parameters>()

val defaultDockerFile
    get() = File(buildDir, dockerFile)

fun isLib() =
    mainFiles.isEmpty()

class GWorkFlow : Plugin<Project>
{
    private lateinit var project : Project

    private inline fun <reified T : Task> createTask(name : String, groupName : String = taskGroupMain) =
        project.tasks.create(name, T::class.java) { group = groupName }

    override fun apply(project : Project)
    {
        extension = project.toExtension(project.objects)

        val branch = Git.branch

        isMainBranch = branch in listOf("main", "master", "prod", "")

        fun calcVars()
        {
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

        fun configureProject()
        {
            project.version = productVer

            project
                .repositories {
                    if (File(dependencyDir).exists())
                        maven {
                            url = project.projectDir.resolve(dependencyDir).toURI()
                        }

                    mavenLocal()

                    if (!params.mavenDependsURL.isEmpty)
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
            //project.plugins.apply("io.gitlab.arturbosch.detekt")

            //project.dependencies.add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit5:1.9.10")
            project.dependencies.add("implementation", "org.testng:testng:7.10.2")

            // fix "java.lang.module.ResolutionException: Modules jetty.servlet.api and jakarta.servlet export package jakarta.servlet.descriptor to module org.testng"
            project.dependencies.add("implementation", "org.eclipse.jetty.toolchain:jetty-servlet-api:4.0.6")

            val java = project.extensions.findByType(JavaPluginExtension::class.java) mustBeFound "JavaPluginExtension"

            if (isLib())
            {
                java.withSourcesJar()
                java.withJavadocJar()

                project.pluginManager.apply(MavenPublishPlugin::class.java)
                project.pluginManager.apply(SigningPlugin::class.java)
                project.pluginManager.apply(DetektPlugin::class.java)
                project.pluginManager.apply(JavaLibraryPlugin::class.java)

                val javaSources = project.extensions.getByType(SourceSetContainer::class.java)

                File("src")
                    .listFiles()
                    ?.let {
                        javaSources["main"].java.srcDirs(it.filter { it.isDirectory && it.name != "test" })
                    }

                javaSources["test"].java.srcDirs(listOf("src/test/kotlin"))
            }
            else
                project.plugins.apply("application")

            val jdk = extension.jdk.orNull ?: "21"
            val jdkVer = JavaLanguageVersion.of(jdk)

            project.extensions
                .getByType<JavaToolchainService>()
                .launcherFor {
                    languageVersion.set(jdkVer)
                }

            project.extensions
                .configure(KotlinJvmProjectExtension::class.java) {
                    jvmToolchain {
                        languageVersion.set(jdkVer)
                    }
                }

            java.toolchain.languageVersion.set(jdkVer)

            project.gradle.startParameter.maxWorkerCount = 8
            project.gradle.startParameter.isParallelProjectExecutionEnabled = true
        }

        fun createTasks()
        {
            createTask<Check>(checkName)
            createTask<Clean>(cleanName)

            if (hasEnv)
            {
                createTask<PrepareEnv>(envUpName)
                createTask<ShutdownEnv>(envDownName)
            }

            project.tasks.create("$GLOBAL_PREFIX-version") {
                group = taskGroupMore

                doLast {
                    println(project.version)
                }
            }

            project.tasks.create("$GLOBAL_PREFIX-name") {
                group = taskGroupMore

                doLast {
                    println(project.name)
                }
            }

            createTask<CheckBranchTask>(checkBranchName)
                .group = taskGroupMore

            createTask<DAV>(toReleaseName)
            createTask<Major>(releaseMajorName)
            createTask<Minor>(releaseMinorName)
            createTask<Patch>(releasePatchName)

            if (isLib())
            {
                createTask<PublishLib>(publishName)
                createTask<DeployLib>(deployName)
                createTask<LibDevFinish>(devFinishName)
                createTask<LibTests>(testName)

                createTask<DeployDependent>(deployDependent)
                    .group = taskGroupMore
            }
            else
            {
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

                createTask<Images>(imagesName)
                createTask<Run>(runName)
                createTask<DevFinish>(devFinishName)
                createTask<Tests>(testName)
                createTask<TestsAfterBuild>(testsAfterBuildName)
            }
        }

        calcVars()
        Git.installHooks()
        configureProject()
        createTasks()
    }
}

val String.patched
    get() = this
        .replace("j-a-r", jarName)
        .replace("v-e-r-s-i-o-n", productVer)
        .replace("a-c-c-o-u-n-t", params.registryUrl.str)
        .replace("m-a-i-n--i-m-a-g-e", Image(params.registryUrl, projectName, productVer).toString())

fun checkMainBranch() =
    isMainBranch orThrow "The task can only be run in the Main branch"