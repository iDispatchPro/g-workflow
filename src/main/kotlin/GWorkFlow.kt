import io.gitlab.arturbosch.detekt.DetektPlugin
import k.common.AnsiColor
import k.common.conFormat
import k.common.env
import k.common.isEmpty
import k.common.low
import k.common.mustBeFound
import k.common.orThrow
import k.common.resetConFormatStr
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
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.support.normaliseLineSeparators
import org.gradle.plugins.signing.SigningPlugin
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import tasks.Build
import tasks.Check
import tasks.CheckBranchTask
import tasks.Clean
import tasks.Deploy
import tasks.DeployDependent
import tasks.DeployLib
import tasks.DevFinish
import tasks.Images
import tasks.IntegrationsTests
import tasks.LibDevFinish
import tasks.LibTests
import tasks.Publish
import tasks.PublishLib
import tasks.Run
import tasks.Tests
import tasks.checkBranchName
import tasks.deployDependent
import tasks.deployName
import tasks.integrationTestsName
import tasks.unitTestsName
import tasks.version.DAV
import tasks.version.Major
import tasks.version.Minor
import tasks.version.Patch
import java.io.File

const val maxJdkVer = 22
const val gradleVersion = "9.0.0"

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

const val toReleaseName = "$GLOBAL_PREFIX-release"

const val releaseMajorName = "$GLOBAL_PREFIX-release-major"
const val releaseMinorName = "$GLOBAL_PREFIX-release-minor"
const val releasePatchName = "$GLOBAL_PREFIX-release-patch"

const val reportColWidth = 66

val params : Parameters = (env("${System.getenv(GRADLE_HOME_VAR)}/$gradlePropsFile")
        + env(gradlePropsFile)
        + env(localPropsFile)
        + env(myPropsFile)).deSerialize<Parameters>()

class GWorkFlow : Plugin<Project>
{
    private lateinit var project : Project
    val context by lazy { TaskContext(project) }

    private inline fun <reified T : Task> createTask(name : String, groupName : String = taskGroupMain)
    {
        project
            .tasks
            .register(name, T::class.java, context)

        project
            .tasks[name]
            .group = groupName
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    override fun apply(project : Project)
    {
        println("\n${" gWorkFlow ".conFormat(backColor = AnsiColor.Blue)}$resetConFormatStr\n")

        this.project = project

        context

        project.plugins.apply("org.jetbrains.kotlin.jvm")

        project
            .afterEvaluate {
                val jdkName = context
                    .extension
                    .jdkName
                    .orNull mustBeFound "gWorkFlow.jdkName"

                val jdkVersion = context
                    .extension
                    .jdkVersion
                    .orNull
                    ?: (jdkName.substringAfterLast('-') mustBeFound "gWorkFlow.jdkVersion").toInt()

                configureJDK(jdkVersion)

                val gradleChanged = configureGradle(gradleVersion)

                val hasTasks = project.gradle.startParameter.taskNames.isNotEmpty()

                val ideChanged = configureIDE(jdkName)

                if (ideChanged || gradleChanged)
                    if (hasTasks)
                        error("Configuration was changed. Please start [${project.gradle.startParameter.taskNames.joinToString(" ")}] again.")
                    else
                        error("Please sync Gradle project.")

                configureProject()
                createTasks()

                println("\nConfiguration finished\n".conFormat(AnsiColor.Green))

                if (hasTasks)
                    println("Start task(s): [${project.gradle.startParameter.taskNames.joinToString(" ")}]...".conFormat(AnsiColor.Smoke))
                else
                    println("Continue...".conFormat(AnsiColor.Smoke))
            }
    }

    private fun createTasks()
    {
        createTask<Check>(checkName)
        createTask<Clean>(cleanName)

        /*if (hasEnv)
        {
            createTask<PrepareEnv>(envUpName)
            createTask<ShutdownEnv>(envDownName)
        }*/

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

        if (context.isLib)
        {
            createTask<PublishLib>(publishName)
            createTask<DeployLib>(deployName)
            createTask<LibDevFinish>(devFinishName)
            createTask<LibTests>(unitTestsName)

            createTask<DeployDependent>(deployDependent)
        } else
        {
            createTask<Build>(buildName)
            createTask<Deploy>(deployName)
            createTask<Publish>(publishName)

            createTask<Images>(imagesName)
            createTask<Run>(runName)
            createTask<DevFinish>(devFinishName)
            createTask<Tests>(unitTestsName)
            createTask<IntegrationsTests>(integrationTestsName)
        }
    }

    private fun configureProject()
    {
        project.version = context.productVersion

        configureRepositories()

        project.plugins.apply("java")
        //project.plugins.apply("io.gitlab.arturbosch.detekt")

        //project.dependencies.add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit5:1.9.10")
        project.dependencies.add("implementation", "org.testng:testng:7.11.0")

        // fix "java.lang.module.ResolutionException: Modules jetty.servlet.api and jakarta.servlet export package jakarta.servlet.descriptor to module org.testng"
        project.dependencies.add("implementation", "org.eclipse.jetty.toolchain:jetty-servlet-api:4.0.6")

        val java = project.extensions.findByType(JavaPluginExtension::class.java) mustBeFound "JavaPluginExtension"

        if (context.isLib)
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

        project.gradle.startParameter.maxWorkerCount = 8
        project.gradle.startParameter.isParallelProjectExecutionEnabled = true
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
        (version <= maxJdkVer) orThrow "Max supported JDK version is $maxJdkVer"

        val jdkVer = JavaLanguageVersion.of(version)

        project
            .extensions
            .configure<KotlinJvmProjectExtension>("kotlin") {
                jvmToolchain {
                    languageVersion.set(jdkVer)
                    compilerOptions.jvmTarget.set(JvmTarget.valueOf("JVM_$version"))
                }

                /*compilerOptions {
                    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
                    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
                }*/
            }

        /*val java = project.extensions.findByType(JavaPluginExtension::class.java) mustBeFound "JavaPluginExtension"

        java.toolchain.languageVersion.set(jdkVer)*/
    }

    private fun configureGradle(version : String) : Boolean
    {
        return patchFile(
            name = "gradle/wrapper/gradle-wrapper.properties",
            default = """
                        distributionBase=GRADLE_USER_HOME
                        distributionPath=wrapper/dists
                        distributionUrl=
                        zipStoreBase=GRADLE_USER_HOME
                        zipStorePath=wrapper/dists
                     """,
            "distributionUrl.*" to """distributionUrl=https\\://services.gradle.org/distributions/gradle-$version-bin.zip"""
                        )
    }

    private fun configureIDE(jdkName : String) : Boolean
    {
        enumJDK().find { it.name == jdkName } mustBeFound "JDK \"$jdkName\""

        val gradleJdkProp =
            """  <component name="ProjectRootManager" version="2" default="true" project-jdk-name="$jdkName" project-jdk-type="JavaSDK" />${"\n"}</project>"""

        val changedJDK = patchFile(
            name = ".idea/misc.xml",
            default = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project version="4">
                    <component name="ExternalStorageConfigurationManager" enabled="true" />
                </project>
            """,
            """<component\s*name\s*=\s*"ProjectRootManager".*?>""" to "",
            "</project>" to gradleJdkProp
                                  )

        return patchFile(
            name = ".idea/gradle.xml",
            default = """
                <?xml version="1.0" encoding="UTF-8"?>
                    <project version="4">
                      <component name="GradleSettings">
                        <option name="linkedExternalProjectsSettings">
                          <GradleProjectSettings>
                            <option name="externalProjectPath" value="${"$"}PROJECT_DIR$" />
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
            """<option\s*name\s*=\s*"gradleJvm".*?/>""" to "",
            """<option\s*name\s*=\s*"delegatedBuild".*?/>""" to "",
            """<option\s*name\s*=\s*"testRunner".*?/>""" to "",
            "<GradleProjectSettings>" to """<GradleProjectSettings>
        <option name="delegatedBuild" value="false" />
        <option name="testRunner" value="PLATFORM" />""".normaliseLineSeparators()
                        ) || changedJDK
    }

    private val String.cleanUp
        get() = lines()
            .filter { !it.isBlank() }
            .joinToString(System.lineSeparator())

    private fun patchFile(name : String, default : String, vararg replace : Pair<String, String>) : Boolean
    {
        val cfgFile = File(context.projectDir, name)

        print("Look for ${name.conFormat(AnsiColor.Blue)}$resetConFormatStr...".padEnd(reportColWidth))

        val content = (if (cfgFile.exists())
            cfgFile.readText().replace("#.*".toRegex(), "")
        else
            default.trimIndent()).cleanUp

        var fixedContent = content

        replace
            .forEach {
                fixedContent = fixedContent.replace(it.first.toRegex(), it.second)
            }

        fixedContent = fixedContent.cleanUp

        val changed = (content != fixedContent) || !cfgFile.exists()

        if (changed)
        {
            println("Patch applied")

            cfgFile.writeText(fixedContent)
        } else
            println("OK")

        return changed
    }
}

fun DefaultTask.autoAfterEvaluate(code : Project.() -> Unit) =
    if (project.state.executed)
        code(project)
    else
        project
            .afterEvaluate(code)

fun Task.execute()
{
    actions.forEach { it.execute(this) }
}