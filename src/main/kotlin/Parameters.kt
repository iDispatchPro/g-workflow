import k.common.list
import k.docker.models.DockerRegistry
import k.serializing.*
import java.net.URI

data class Parameters(val registryUrl : String,
                      @Optional
                      val registryLogin : String,
                      @Optional
                      val registryPassword : String,
                      @Optional
                      val mavenDependsURL : URI,
                      @Optional
                      val mavenPluginsURL : URI,
                      @Optional
                      val mavenLogin : String,
                      @Optional
                      val mavenPassword : String,
                      @Optional
                      val signingKeyId : String,
                      @Optional
                      val signingPublicKeyFile : String,
                      @Optional
                      val signingKeyRingFile : String,
                      @Optional
                      val signingPassFraze : String)
{
    val registry : DockerRegistry
        get() = DockerRegistry(registryUrl.list.first(), registryLogin, registryPassword)
}