package tasks

import TaskContext
import autoAfterEvaluate
import computeAndSaveFileHash
import devFinishName
import fromScript
import k.common.*
import k.serializing.deSerialize
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.gradle.api.DefaultTask
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.plugins.signing.SigningExtension
import params
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

const val keyServer = "https://keyserver.ubuntu.com/pks"

open class PublishLib @Inject constructor(private val context: TaskContext) : DefaultTask()
{
    private lateinit var mavenPublish : MavenPublicationInternal
    private lateinit var groupIdValue : String

    private val zipFile = project.layout.buildDirectory.get().asFile.resolve("upload.zip")

    init
    {
        description = "Publish a library to maven repository."

        /*val publishStdName = "publish${projectName.title}PublicationToMavenRepository"

        project.tasks.getByName(publishStdName).mustRunAfter(devFinishName)

        dependsOn(publishStdName, devFinishName)*/

        autoAfterEvaluate {
            dependsOn(checkBranchName)

            if (context.isMainBranch)
                dependsOn(devFinishName,
                          "javadocJar",
                          "sourcesJar",
                          "generatePomFileForMavenPublication",
                          "generateMetadataFileForMavenPublication")
            else
                dependsOn(devFinishName,
                          "publishToMavenLocal")

            groupIdValue = context.extension.groupId fromScript "group"

            extensions
                .getByType(PublishingExtension::class.java)
                .apply {
                    publications {
                        mavenPublish = create<MavenPublication>("Maven") {
                            from(project.components["java"])

                            groupId = groupIdValue
                            artifactId = context.projectName
                            version = context.productVersion

                            val projectUrl = context.extension.projectUrl fromScript "projectUrl"

                            pom.url = projectUrl
                            pom.description = context.extension.projectDescription fromScript "projectDescription"

                            pom.scm {
                                url = context.extension.scmUrl.orNull ?: projectUrl
                            }

                            pom.licenses {
                                license {
                                    url = context.extension.licenseUrl.orNull ?: projectUrl
                                }
                            }

                            pom.developers {
                                developer {
                                    url = context.extension.developerUrl.orNull ?: projectUrl
                                }
                            }
                        } as MavenPublicationInternal
                    }

                    repositories {
                        if (context.isMainBranch)
                        {
                            maven {
                                url = params.mavenPluginsURL

                                credentials {
                                    username = params.mavenLogin
                                    password = params.mavenPassword
                                }
                            }
                        }
                        else
                            mavenLocal()
                    }
                }
        }
    }

    @TaskAction
    fun action()
    {
        if (context.isMainBranch)
        {
            ensureKey()

            val files = collectFiles()

            pack(files + hash(files) + sign(files))
            upload()
        }

        val target = if (context.isMainBranch)
            "Maven Central"
        else
            "Maven Local"

        msg("\nPlease use this line for importing library from $target:".n, MsgType.BlueText)
        msg(
            """implementation("$groupIdValue:${context.projectName}:${context.productVersion}")""".n,
            MsgType.Ok
        )
    }

    private fun ensureKey()
    {
        println("\nLook for public key in $keyServer...")

        val check = call(Request.Builder()
                             .get()
                             .url("$keyServer/lookup?search=${params.signingKeyId}&fingerprint=on&op=index"))

        if (check.code == 404)
        {
            println("\nKey not found. Try to upload...")

            val body = "keytext=${File(params.signingPublicKeyFile).text mustBeFound params.signingPublicKeyFile}"
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())

            val publish = call(Request.Builder()
                                   .post(body)
                                   .url("$keyServer/add"))

            handleResponse(publish, "upload public key")
        }
        else
        {
            handleResponse(check, "check public key")

            println("\nKey found. Use existing...")
        }
    }

    private infix fun String?.fromProps(name : String) =
        mustBeSpecified("$name in *.properties file")

    private fun hash(files : List<File>) : List<File>
    {
        println("\nHashing...")

        return files
            .flatMap { file ->
                computeAndSaveFileHash(file, listOf("SHA-256", "SHA-512", "MD5", "SHA-1"))
            }
    }

    private fun sign(files : List<File>) : List<File>
    {
        println("\nSigning...")

        val signer = project.extensions.getByType(SigningExtension::class.java)

        signer
            .useInMemoryPgpKeys(params.signingKeyId fromProps "signingKeyId",
                                File(params.signingKeyRingFile fromProps "signingKeyRingFile").text,
                                params.signingPassFraze fromProps "signingPassFraze")

        return files
            .map {
                signer.sign(it)

                File(it.absolutePath + ".asc")
            }
    }

    private fun collectFiles() : List<File>
    {
        println("\nPrepare...")

        val commonFileName = "${context.projectName}-${context.productVersion}"

        return mavenPublish.publishableArtifacts
            .map { artifact ->
                val newFile = File(artifact.file.parentFile, when (artifact.file.name)
                {
                    "pom-default.xml" -> "$commonFileName.pom"
                    "module.json"     -> "$commonFileName.module"
                    else              -> artifact.file.name
                })

                artifact.file.renameTo(newFile)

                newFile
            }
    }

    private fun pack(files : List<File>)
    {
        println("\nPacking...")

        val path = groupIdValue.str.replace(".", "\\") + "\\${context.projectName}\\${context.productVersion}"

        ZipOutputStream(FileOutputStream(zipFile))
            .use { zipOut ->
                val data = ByteArray(1024)

                files
                    .forEach { file ->
                        FileInputStream(file)
                            .use { stream ->
                                zipOut.putNextEntry(ZipEntry("$path\\${file.name}"))

                                var length = stream.read(data)

                                while (length != -1)
                                {
                                    zipOut.write(data, 0, length)
                                    length = stream.read(data)
                                }

                                zipOut.closeEntry()
                            }
                    }

                zipOut.close()
            }
    }

    private fun upload()
    {
        val name = URLEncoder.encode("$groupIdValue:${context.projectName}:${context.productVersion}", UTF_8)

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
                               errorExtractor : (String) -> String = { it })
    {
        val responseBody = response.body?.string()

        if (!response.isSuccessful)
            error("Failed to $failureMessage\nHTTP Status Code: ${response.code}\nError Message: ${errorExtractor(responseBody.orEmpty())}")
    }
}