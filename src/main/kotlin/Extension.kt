import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

open class Extension(objectFactory : ObjectFactory) {
    val jdk : Property<String> = objectFactory.property(String::class.java)
    val skipImageCheck : Property<Boolean> = objectFactory.property(Boolean::class.java)

    init {
        jdk.set("21")
        skipImageCheck.set(false)
    }

    companion object {
        internal fun Project.toExtension(objectFactory : ObjectFactory) : Extension =
            Extension(objectFactory)
                .also {
                    extensions.add("gWorkFlow", it)
                }
    }
}