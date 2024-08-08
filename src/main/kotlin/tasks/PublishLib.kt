package tasks

import devFinishName
import k.common.*
import org.gradle.api.DefaultTask
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.signing.SigningExtension
import params
import productVer
import projectName
import java.io.File

open class PublishLib : DefaultTask() {
    private lateinit var mavenPublish : MavenPublicationInternal
    private val zipPath = project.layout.buildDirectory.get().asFile.resolve("upload.zip").absolutePath

    init {
        description = "Publish a library to maven repository."

        /*val publishStdName = "publish${projectName.title}PublicationToMavenRepository"

        project.tasks.getByName(publishStdName).mustRunAfter(devFinishName)

        dependsOn(publishStdName, devFinishName)*/

        dependsOn(devFinishName)

        project.extensions.configure<PublishingExtension>("publishing") {
            publications {
                mavenPublish = create<MavenPublication>(projectName) {
                    from(project.components["java"])

                    groupId = project.group.str
                    artifactId = projectName
                    version = productVer
                } as MavenPublicationInternal
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

    @TaskAction
    fun action() {
        sign()
        pack()
        upload()

        msg("Current $projectName version: $productVer".n.n, MsgType.BlueText)
        msg("Please use this line for importing library:".n, MsgType.BlueText)
        msg("""implementation("${project.group}:$projectName:$productVer")""".n, MsgType.OrangeText)
    }

    private fun sign() {
        msg("Signing artifacts...")

        val signer = project.extensions.getByType(SigningExtension::class.java)

        mavenPublish.publishableArtifacts
            .forEach {
                signer.sign { it.file }
            }
    }

    private fun pack() {
        msg("Packing files...")

        val buildDirectory = project.layout.buildDirectory

        val buildFiles = buildDirectory
            .dir("libs")
            .get()
            .asFileTree
            .files
            // TODO: Не собирать изначально
            .filter { file ->
                !file.name.endsWith("-plain.jar") && !file.name.endsWith("-plain.jar.asc")
            }

        val mavenFiles = buildDirectory
            .dir("publications/maven")
            .orNull
            ?.asFileTree
            ?.map { file : File ->
                File(when (file.name) {
                         "pom-default.xml"     -> "$projectName-$productVer.pom"
                         "pom-default.xml.asc" -> "$projectName-$productVer.pom.asc"
                         "module.json"         -> "$projectName-$productVer.module"
                         "module.json.asc"     -> "$projectName-$productVer.module.asc"
                         else                  -> "$projectName-$productVer.${file.name}"
                     })
            }

        val versionCatalogDir = buildDirectory.dir("version-catalog").orNull
        versionCatalogDir?.asFileTree?.forEach { file ->

            val fileName = file.name
            val newName =
                when {
                    fileName.endsWith("versions.toml")     -> "$projectName-$productVer.toml"
                    fileName.endsWith("versions.toml.asc") -> "$projectName-$productVer.toml.asc"
                    else                                   -> fileName
                }

            filesToAggregate.addLast(renameFile(file, newName))
        }

        val tempDirFile = createDirectoryStructure(directoryPath)

        filesToAggregate.forEach { file ->
            tempDirFile.let {
                file.copyTo(it.resolve(file.name), overwrite = true)
            }
        }

        ZipUtils.prepareZipFile(it, zipPath)
    }

    private fun upload() {
        msg("Upload to repo...")

    }
}