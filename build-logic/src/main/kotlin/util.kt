import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.named

inline fun PublicationContainer.codebook(crossinline conf: MavenPublication.() -> Unit) {
    named<MavenPublication>("codebook") {
        conf()
    }
}
