import k.common.list
import k.docker.models.DockerRegistry
import k.marshalling.Optional
import java.net.URI

data class Parameters(val registryUrl : String,
                      @Optional
                      val registryLogin : String,
                      @Optional("")
                      val registryPassword : String,
                      @Optional(defaultGroup)
                      val group : String,
                      val mavenDependsURL : URI,
                      val mavenPluginsURL : URI,
                      val mavenLogin : String,
                      val mavenPassword : String,
                      @Optional("21")
                      val jdkVer : String)
{
    val registry : DockerRegistry
        get() = DockerRegistry(registryUrl.list.first(), registryLogin, registryPassword)
}