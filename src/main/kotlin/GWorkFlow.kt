import Extension.Companion.toExtension
import io.gitlab.arturbosch.detekt.DetektPlugin
import k.common.env
import k.common.isEmpty
import k.common.low
import k.common.mustBeFound
import k.common.orThrow
import k.common.str
import k.common.text
import k.docker.models.Image
import k.serializing.deSerialize
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.repositories
import org.gradle.plugins.signing.SigningPlugin
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import tasks.Build
import tasks.Check
import tasks.Clean
import tasks.Deploy
import tasks.DeployDependent
import tasks.DeployLib
import tasks.DevFinish
import tasks.Images
import tasks.IntegrationsTests
import tasks.LibDevFinish
import tasks.LibTests
import tasks.PrepareEnv
import tasks.Publish
import tasks.PublishLib
import tasks.Run
import tasks.ShutdownEnv
import tasks.Tests
import tasks.deployDependent
import tasks.deployName
import tasks.dockerFile
import tasks.hasEnv
import tasks.integrationTestsName
import tasks.unitTestsName
import tasks.version.CheckBranchTask
import tasks.version.DAV
import tasks.version.Major
import tasks.version.Minor
import tasks.version.Patch
import tasks.version.checkBranchName
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

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

const val publishName = "$GLOBAL_PREFIX-publish"
const val buildName = "$GLOBAL_PREFIX-build"
const val checkName = "$GLOBAL_PREFIX-check"
const val imagesName = "$GLOBAL_PREFIX-images"
const val envUpName = "$GLOBAL_PREFIX-env-up"
const val envDownName = "$GLOBAL_PREFIX-env-down"
const val removeImages = "$GLOBAL_PREFIX-remove-images"
const val cleanName = "$GLOBAL_PREFIX-clean"
const val runName = "$GLOBAL_PREFIX-run"
const val devFinishName = "$GLOBAL_PREFIX-dev-finish"
const val maxJdkForKotlinCompiler = 22

const val toReleaseName = "$GLOBAL_PREFIX-release"

const val releaseMajorName = "$GLOBAL_PREFIX-release-major"
const val releaseMinorName = "$GLOBAL_PREFIX-release-minor"
const val releasePatchName = "$GLOBAL_PREFIX-release-patch"

lateinit var jarName : String
lateinit var fullJarName : String
lateinit var productVer : String
lateinit var projectName : String
lateinit var buildDir : String
lateinit var projectDir : File
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
        project
            .tasks
            .register(name, T::class.java) { group = groupName }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    override fun apply(project : Project)
    {
        println("\ngWorkFlow started!\n")

        extension = project.toExtension(project.objects)

        val branch = Git.branch

        isMainBranch = branch in listOf("main", "master", "prod", "")

        fun calcVars()
        {
            this.project = project
            buildDir = project.layout.buildDirectory.get().str
            projectDir = project.projectDir
            projectName = project.name
            versionFile = File(projectDir, VERSION_FILE)
            dateStr = SimpleDateFormat("yy.M.d.HHmm").format(Date())

            productVer = if (versionFile.exists())
                versionFile.text.trim()
            else
                dateStr

            jarName = "${project.name.lowercase()}.$productVer.jar"
            fullJarName = "$buildDir/$jarName"
        }

        fun configureProject()
        {
            project.version = productVer

            configureRepositories()

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
            } else
                project.plugins.apply("application")

            project
                .afterEvaluate {
                    val jdkName = extension.jdkName.orNull mustBeFound "gWorkFlow.jdkName"

                    val jdkVersion = extension
                        .jdkVersion
                        .orNull
                        ?: (jdkName.substringAfterLast('-') mustBeFound "gWorkFlow.jdkVersion").toInt()

                    configureJDK(min(jdkVersion, maxJdkForKotlinCompiler))
                    configureIDE(jdkName)
                }

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

            project
                .tasks
                .register("$GLOBAL_PREFIX-version") {
                    group = taskGroupMore

                    doLast {
                        println(project.version)
                    }
                }

            project
                .tasks
                .register("$GLOBAL_PREFIX-name") {
                    group = taskGroupMore

                    doLast {
                        println(project.name)
                    }
                }

            createTask<CheckBranchTask>(checkBranchName, taskGroupMore)
            createTask<DAV>(toReleaseName)
            createTask<Major>(releaseMajorName)
            createTask<Minor>(releaseMinorName)
            createTask<Patch>(releasePatchName)

            if (isLib())
            {
                createTask<PublishLib>(publishName)
                createTask<DeployLib>(deployName)
                createTask<LibDevFinish>(devFinishName)
                createTask<LibTests>(unitTestsName)

                createTask<DeployDependent>(deployDependent)
            } else
            {
                project
                    .tasks
                    .register(buildName, Build::class.java) {
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
                createTask<Tests>(unitTestsName)
                createTask<IntegrationsTests>(integrationTestsName)
            }
        }

        calcVars()
        Git.installHooks()
        configureProject()
        createTasks()
    }

    private fun configureRepositories()
    {
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
    }

    private fun configureJDK(version : Int)
    {
        val jdkVer = JavaLanguageVersion.of(version)

      /*  project
            .extensions
            .configure<JavaToolchainService>{
                toolchain {
                    languageVersion.set(jdkVer)
                    vendor.set(JvmVendorSpec.ADOPTIUM)
                }
            }*/

/*        project
            .extensions
            .configure<JavaPluginExtension> {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(11))
                }

                targetCompatibility = JavaVersion.valueOf("VERSION_$version")
            }*/

/*        project
            .dependencies
            .add("implementation", "org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
*/
        project
            .extensions
            .configure<KotlinJvmProjectExtension> {
                jvmToolchain {
                    compilerOptions.jvmTarget.set(JvmTarget.valueOf("JVM_$version"))
                    languageVersion.set(jdkVer)
                }
            }
    }

    private fun configureIDE(jdkName : String)
    {
        pathXML(
            name = ".idea/misc.xml",
            default = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project version="4">
                    <component name="ExternalStorageConfigurationManager" enabled="true" />
                </project>
            """,
            """<component\s*name\s*=\s*"ProjectRootManager".*?>""" to "",
            "</project>" to """  <component name="ProjectRootManager" version="2" default="true" project-jdk-name="$jdkName" project-jdk-type="JavaSDK" />${"\n"}</project>"""
               )

        pathXML(
            name = ".idea/gradle.xml",
            default = """
                <?xml version="1.0" encoding="UTF-8"?>
                    <project version="4">
                      <component name="GradleSettings">
                        <option name="linkedExternalProjectsSettings">
                          <GradleProjectSettings>
                            <option name="externalProjectPath" value="${"$"}PROJECT_DIR$" />
                            <option name="gradleJvm" value="graalvm-ce-23" />
                            <option name="modules">
                              <set>
                                <option value="${"$"}PROJECT_DIR$" />
                              </set>
                            </option>
                          </GradleProjectSettings>
                        </option>
                      </component>
                    </project>
            """,
            """<option\s*name\s*=\s*"gradleJvm".*?/>""" to """<option name="gradleJvm" value="21" />"""
               )
    }

    private fun pathXML(name: String, default : String, vararg replace : Pair<String, String>) {
        val xmlFile = File(projectDir, name)

        val content = if (xmlFile.exists())
            xmlFile.readText()
        else
            default.trimIndent()

        var fixedContent = content

        replace
            .forEach {
                fixedContent = fixedContent.replace(it.first.toRegex(), it.second)
            }

        fixedContent = fixedContent
            .lines()
            .filter { it.isNotBlank() }
            .joinToString("\n")

        if (content != fixedContent)
        {
            println("Patching ${xmlFile}File...")

            xmlFile.writeText(fixedContent)
        }
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

fun DefaultTask.autoAfterEvaluate(code : Project.() -> Unit) =
    if (project.state.executed)
        code(project)
    else
        project
            .afterEvaluate(code)