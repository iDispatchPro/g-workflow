import Extension.Companion.toExtension
import io.gitlab.arturbosch.detekt.DetektPlugin
import k.common.*
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
import tasks.*
import tasks.version.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

const val maxJdkVer = 24
const val gradleVersion = "8.14-rc-2"

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

const val reportColWidth = 66

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
        println("\n${" gWorkFlow ".conFormat(backColor = AnsiColor.Blue)}$resetConFormatStr\n")

        this.project = project
        extension = project.toExtension(project.objects)

        val branch = Git.branch

        isMainBranch = branch in listOf("main", "master", "prod", "")

        calcVars()

        project
            .afterEvaluate {
                val jdkName = extension.jdkName.orNull mustBeFound "gWorkFlow.jdkName"

                val jdkVersion = extension
                    .jdkVersion
                    .orNull
                    ?: (jdkName.substringAfterLast('-') mustBeFound "gWorkFlow.jdkVersion").toInt()

                (jdkVersion <= maxJdkVer) orThrow "Max supported JDK version is $maxJdkVer"

                configureJDK(min(jdkVersion, maxJdkForKotlinCompiler))

                val gradleChanged = configureGradle(gradleVersion)

                if (gradleChanged && project.gradle.startParameter.taskNames.isNotEmpty())
                    doRestart("Gradle configuration was changed.")

                val ideChanged = configureIDE(jdkName)

                if (ideChanged && project.gradle.startParameter.taskNames.isNotEmpty())
                    doRestart("IDE configuration was changed.")

                println("\nConfiguration finished\n".conFormat(AnsiColor.Green))
                println("Start task(s): [${project.gradle.startParameter.taskNames.joinToString(" ")}]...".conFormat(AnsiColor.Smoke))
            }

        Git.installHooks()
        configureProject()
        createTasks()
    }

    private fun doRestart(reason : String) {
        error("$reason Please start [${project.gradle.startParameter.taskNames.joinToString(" ")}] again.")
    }

    private fun createTasks()
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

    private fun configureProject()
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

        project.gradle.startParameter.maxWorkerCount = 8
        project.gradle.startParameter.isParallelProjectExecutionEnabled = true
    }

    private fun calcVars()
    {
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

        project
            .extensions
            .configure<KotlinJvmProjectExtension> {
                jvmToolchain {
                    compilerOptions.jvmTarget.set(JvmTarget.valueOf("JVM_$version"))
                    languageVersion.set(jdkVer)
                }
            }
    }

    private fun configureGradle(version : String) : Boolean
    {
        return pathFile(
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
        val gradleJdkProp = """  <component name="ProjectRootManager" version="2" default="true" project-jdk-name="$jdkName" project-jdk-type="JavaSDK" />${"\n"}</project>"""

        var changedJDK = pathFile(
            name = ".idea/misc.xml",
            default = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project version="4">
                    <component name="ExternalStorageConfigurationManager" enabled="true" />
                    $gradleJdkProp
                </project>
            """,
            """<component\s*name\s*=\s*"ProjectRootManager".*?>""" to "",
            "</project>" to gradleJdkProp
                                 )

        changedJDK = changedJDK || pathFile(
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
            """<option\s*name\s*=\s*"gradleJvm".*?/>""" to ""
                                           )
        return changedJDK
    }

    private val String.cleanUp
        get() = lines()
            .filter { !it.isBlank() }
            .joinToString(System.lineSeparator())

    private fun pathFile(name: String, default : String, vararg replace : Pair<String, String>) : Boolean
    {
        val cfgFile = File(projectDir, name)

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

        val changed = content != fixedContent

        if (changed)
        {
            println("Patch applied")

            cfgFile.writeText(fixedContent)
        } else
            println("OK")

        return changed
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

fun Task.execute()
{
    actions.forEach { it.execute(this) }
}