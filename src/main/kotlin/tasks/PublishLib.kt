package tasks

import computeAndSaveFileHash
import devFinishName
import k.common.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
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
import java.io.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.*

open class PublishLib : DefaultTask() {
    private lateinit var mavenPublish : MavenPublicationInternal
    private val zipFile = project.layout.buildDirectory.get().asFile.resolve("upload.zip")

    init {
        description = "Publish a library to maven repository."

        /*val publishStdName = "publish${projectName.title}PublicationToMavenRepository"

        project.tasks.getByName(publishStdName).mustRunAfter(devFinishName)

        dependsOn(publishStdName, devFinishName)*/

        dependsOn(devFinishName,
                  "javadocJar",
                  "sourcesJar",
                  "generatePomFileForMavenPublication",
                  "generateMetadataFileForMavenPublication")

        project.extensions.configure<PublishingExtension>("publishing") {
            publications {
                mavenPublish = create<MavenPublication>("Maven") {
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

        msg("\nCurrent $projectName version: $productVer".n.n, MsgType.BlueText)
        msg("Please use this line for importing library:".n, MsgType.BlueText)
        msg("""implementation("${project.group}:$projectName:$productVer")""".n, MsgType.OrangeText)
    }

    private fun sign() {
        println("\nSigning artifacts...")

        val signer = project.extensions.getByType(SigningExtension::class.java)

        mavenPublish.publishableArtifacts
            .forEach {
                signer.sign { it.file }
            }
    }

    private fun pack() {
        println("\nPacking files...")

        val buildDirectory = project.layout.buildDirectory

        val buildFiles = buildDirectory
            .dir("libs")
            .get()
            .asFileTree
            .files

        val commonFileName = "$projectName-$productVer"

        val publishDir = buildDirectory.dir("publications/maven").get()

        val mavenFiles = publishDir
            .asFileTree
            .map { file : File ->
                val newFile = File(publishDir.asFile, when (file.name) {
                    "pom-default.xml"     -> "$commonFileName.pom"
                    "pom-default.xml.asc" -> "$commonFileName.pom.asc"
                    "module.json"         -> "$commonFileName.module"
                    "module.json.asc"     -> "$commonFileName.module.asc"
                    else                  -> "$commonFileName.${file.name}"
                })

                file.renameTo(newFile)

                newFile
            }

        val files = buildFiles + mavenFiles

        val allFiles = files + files.flatMap { file ->
            computeAndSaveFileHash(file, listOf(/*"SHA-256", "SHA-512", */"MD5", "SHA-1"))
        }

        ZipOutputStream(FileOutputStream(zipFile))
            .use { zipOut ->
                val data = ByteArray(1024)

                allFiles
                    .forEach { file ->
                        FileInputStream(file)
                            .use { fi ->
                                zipOut.putNextEntry(ZipEntry(file.name))

                                var length = fi.read(data)

                                while (length != -1) {
                                    zipOut.write(data, 0, length)
                                    length = fi.read(data)
                                }
                            }
                    }
            }
    }

    private fun upload() {
        val name = URLEncoder.encode("${project.group}:$projectName:$productVer", UTF_8)

        val url = "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC&name=$name"

        println("\nUpload to ${url}...")

        val encodedCredentials = "${params.mavenLogin}:${params.mavenPassword}".base64

        val body = MultipartBody
            .Builder()
            .addFormDataPart("bundle",
                             "upload.zip",
                             zipFile.asRequestBody("application/zip".toMediaType()))
            .build()

        val request = Request
            .Builder()
            .post(body)
            .addHeader("Authorization", "UserToken $encodedCredentials")
            .url(url)
            .build()

        val response = OkHttpClient.Builder().build().newCall(request).execute()

        handleResponse(response,
                       successMessage = "Published to Maven central. Deployment ID:",
                       failureMessage = "Cannot publish.")
    }

    data class ErrorMessage(val error : Error)

    data class Error(val message : String)

    private fun handleResponse(response : Response,
                               successMessage : String,
                               failureMessage : String) {
        val responseBody = response.body?.string()

        if (!response.isSuccessful) {
            val statusCode = response.code

            //val errorMessage = responseBody?.deSerialize<ErrorMessage>()
            //                ?: ErrorMessage(Error("Unknown Error: $responseBody"))

            //  println("$failureMessage\nHTTP Status Code: $statusCode\nError Message: ${errorMessage.error.message}")
            println(response.message)
            println(response.code)
        }
        else
            println("$successMessage\n$responseBody")
    }
}