pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://alphacephei.com/maven/") }
        // sherpa-onnx (k2-fsa) публикует Android AAR через JitPack, не на Maven Central.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "EnglishVoiceTutor"
include(":app")
