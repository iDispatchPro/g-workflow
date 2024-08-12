import k.common.list
import k.docker.models.DockerRegistry
import k.serializing.*
import java.net.URI

data class Parameters(val registryUrl : String,
                      @Optional
                      val registryLogin : String,
                      @Optional("")
                      val registryPassword : String,
                      val mavenDependsURL : URI,
                      val mavenPluginsURL : URI,
                      val mavenLogin : String,
                      val mavenPassword : String,
                      @Optional
                      @Name("signing.keyId")
                      val signingKeyId : String,
                      @Optional
                      @Name("signing.publicKeyFile")
                      val signingPublicKeyFile : String) {
    val registry : DockerRegistry
        get() = DockerRegistry(registryUrl.list.first(), registryLogin, registryPassword)
}