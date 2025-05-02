package tasks

import TaskContext
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.get
import javax.inject.Inject

open class LibTests @Inject constructor(private val context: TaskContext) : Test()
{
    init
    {
        description = "Running library tests."

        useTestNG {
            parallel = "classes"
            threadCount = 10
        }

        testClassesDirs = project.extensions.getByType(SourceSetContainer::class.java)["test"].runtimeClasspath
        classpath = testClassesDirs
    }

    override fun getDryRun() =
        project.objects.property(Boolean::class.java).also { it.set(false) }
}