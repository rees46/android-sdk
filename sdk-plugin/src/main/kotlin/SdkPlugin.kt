import org.gradle.api.Plugin
import org.gradle.api.Project

class SdkPlugin : Plugin<Project> {

    companion object {
        private const val FIREBASE_BOM_VERSION = "32.7.0"
        private const val FIREBASE_MESSAGING_VERSION = "23.4.1"
        private const val MEDIA3_EXOPLAYER_VERSION = "1.2.1"
        private const val MEDIA3_UI_VERSION = "1.2.1"
        private const val DAGGER_VERSION = "2.51.1"
        private const val DAGGER_COMPILER_VERSION = "2.48"

        private const val IMPLEMENTATION = "implementation"
        private const val KAPT = "kapt"
    }

    override fun apply(project: Project) {
        project.dependencies.apply {
            add(IMPLEMENTATION, "com.google.firebase:firebase-bom:$FIREBASE_BOM_VERSION")
            add(IMPLEMENTATION, "com.google.firebase:firebase-messaging:$FIREBASE_MESSAGING_VERSION")
            add(IMPLEMENTATION, "androidx.media3:media3-exoplayer:$MEDIA3_EXOPLAYER_VERSION")
            add(IMPLEMENTATION, "androidx.media3:media3-ui:$MEDIA3_UI_VERSION")
            add(IMPLEMENTATION, "com.google.dagger:dagger:$DAGGER_VERSION")
            add(KAPT, "com.google.dagger:dagger-compiler:$DAGGER_COMPILER_VERSION")
        }
    }
}
