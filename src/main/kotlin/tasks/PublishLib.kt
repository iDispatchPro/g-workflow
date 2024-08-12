package tasks

import computeAndSaveFileHash
import devFinishName
import extension
import extensionName
import k.common.*
import k.serializing.deSerialize
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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

const val keyServer = "https://keyserver.ubuntu.com/pks"

open class PublishLib : DefaultTask() {
    private lateinit var mavenPublish : MavenPublicationInternal
    private lateinit var groupIdValue : String

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

        project
            .afterEvaluate {
                groupIdValue = extension.groupId.get() mustBeSpecified "$extensionName.group"

                extensions.configure<PublishingExtension>("publishing") {
                    publications {
                        mavenPublish = create<MavenPublication>("Maven") {
                            from(project.components["java"])

                            groupId = groupIdValue
                            artifactId = projectName
                            version = productVer

                            pom.url = extension.projectUrl.get() mustBeSpecified "$extensionName.projectUrl"
                            pom.description = extension.projectDescription.get() mustBeSpecified "$extensionName.projectDescription"

                            pom.scm { url = "http://x.y.zz" }
                            //pom.issueManagement { url = "https://x.y.zzz" }
                            pom.licenses {
                                license {
                                    url = "http://x.y.zzzz"
                                }
                            }
                            pom.developers {
                                developer {
                                    url = "http://x.y.zzzzz"
                                }
                            }

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
    }

    @TaskAction
    fun action() {
        ensureKey()
        sign()
        pack()
        upload()

        msg("\nPlease use this line for importing library:".n, MsgType.BlueText)
        msg("""implementation("$groupIdValue:$projectName:$productVer")""".n, MsgType.OrangeText)
    }

    private fun ensureKey() {
        println("\nLook for public key in $keyServer...")

        val check = call(Request.Builder()
                             .get()
                             .url("$keyServer/lookup?search=${params.signingKeyId}&fingerprint=on&op=index"))

        if (check.code == 404) {
            println("\nKey not found. Try to upload...")

            val body = "keytext=${File(params.signingPublicKeyFile).text mustBeFound params.signingPublicKeyFile}"
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())

            val publish = call(Request.Builder()
                                   .post(body)
                                   .url("$keyServer/add"))

            handleResponse(publish, "upload public key")
        }
        else {
            println("\nKey found. Use existing...")

            handleResponse(check, "check public key")
        }
    }

    private fun sign() {
        println("\nSigning artifacts...")

        val signer = project.extensions.getByType(SigningExtension::class.java)

        mavenPublish.publishableArtifacts
            .forEach {
                signer.sign(it.file)
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

        val allFiles = files + files
            .flatMap { file ->
                computeAndSaveFileHash(file, listOf("SHA-256", "SHA-512", "MD5", "SHA-1"))
            }

        val path = groupIdValue.str.replace(".", "\\") + "\\$projectName\\$productVer"

        ZipOutputStream(FileOutputStream(zipFile))
            .use { zipOut ->
                val data = ByteArray(1024)

                allFiles
                    .forEach { file ->
                        FileInputStream(file)
                            .use { stream ->
                                zipOut.putNextEntry(ZipEntry("$path\\${file.name}"))

                                var length = stream.read(data)

                                while (length != -1) {
                                    zipOut.write(data, 0, length)
                                    length = stream.read(data)
                                }

                                zipOut.closeEntry()
                            }
                    }

                zipOut.close()
            }
    }

    private fun upload() {
        val name = URLEncoder.encode("$groupIdValue:$projectName:$productVer", UTF_8)

        val url = "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC&name=$name"

        println("\nUpload to ${url}...")

        val encodedCredentials = "${params.mavenLogin}:${params.mavenPassword}".base64

        val body = MultipartBody
            .Builder()
            .addFormDataPart("bundle",
                             "upload.zip",
                             zipFile.asRequestBody("application/zip".toMediaType()))
            .build()

        val response = call(Request.Builder()
                                .post(body)
                                .addHeader("Authorization", "UserToken $encodedCredentials")
                                .url(url))


        handleResponse(response, "upload artifacts") { it.deSerialize<ErrorMessage>().error.message }
    }

    private fun call(request : Request.Builder) =
        OkHttpClient.Builder().build().newCall(request.build()).execute()

    data class ErrorMessage(val error : Error)

    data class Error(val message : String)

    private fun handleResponse(response : Response,
                               failureMessage : String,
                               errorExtractor : (String) -> String = { it }) {
        val responseBody = response.body?.string()

        if (!response.isSuccessful)
            error("Failed to $failureMessage\nHTTP Status Code: ${response.code}\nError Message: ${errorExtractor(responseBody ?: "")}")
    }
}