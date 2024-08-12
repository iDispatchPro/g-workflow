import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

const val extensionName = "gWorkFlow"

open class Extension(objectFactory : ObjectFactory) {
    val groupId : Property<String> = objectFactory.property(String::class.java)
    val projectDescription : Property<String> = objectFactory.property(String::class.java)
    val projectUrl : Property<String> = objectFactory.property(String::class.java)

    val jdk : Property<String> = objectFactory.property(String::class.java)
    val skipImageCheck : Property<Boolean> = objectFactory.property(Boolean::class.java)

    init {
        groupId.set("")
        jdk.set("21")
        skipImageCheck.set(false)
    }

    companion object {
        internal fun Project.toExtension(objectFactory : ObjectFactory) : Extension =
            Extension(objectFactory)
                .also {
                    extensions.add(extensionName, it)
                }
    }
}