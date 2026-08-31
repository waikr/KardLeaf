import java.util.Properties
import com.android.build.api.variant.ApplicationVariantBuilder

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("org.jlleitschuh.gradle.ktlint")
    id("androidx.navigation.safeargs.kotlin")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun readGitOutput(vararg args: String): String = runCatching {
    val process = ProcessBuilder("git", "-c", "core.quotePath=false", *args)
        .directory(rootProject.projectDir)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() == 0 && output.isNotBlank()) output else "unknown"
}.getOrDefault("unknown")

fun readGitLines(vararg args: String): List<String>? = runCatching {
    val process = ProcessBuilder("git", "-c", "core.quotePath=false", *args)
        .directory(rootProject.projectDir)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    val output = process.inputStream.bufferedReader().readLines()
    if (process.waitFor() == 0) output.filter { it.isNotBlank() } else null
}.getOrNull()

fun buildConfigString(value: String): String =
    "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n") + "\""

val currentGitCommit = readGitOutput("rev-parse", "--short", "HEAD")
val currentGitWorktree = readGitOutput("rev-parse", "--show-toplevel")
val currentGitBranch = readGitOutput("branch", "--show-current").let { branch ->
    if (branch != "unknown") branch else readGitOutput("rev-parse", "--abbrev-ref", "HEAD")
}
val currentGitMessage = readGitOutput("log", "-1", "--pretty=%s")
val currentGitChangedFilePaths: List<String>? = run {
    val trackedFiles = readGitLines("diff", "--name-only", "HEAD")
    val untrackedFiles = readGitLines("ls-files", "--others", "--exclude-standard")
    if (trackedFiles == null || untrackedFiles == null) {
        null
    } else {
        (trackedFiles + untrackedFiles).distinct().sorted()
    }
}
val currentGitChangedFiles = currentGitChangedFilePaths?.let { files ->
    when {
        files.isEmpty() -> "无未提交文件"
        files.size <= 50 -> "${files.size} 个：" + files.joinToString(" | ") { it.substringAfterLast('/') }
        else -> "${files.size} 个：" + files.take(50).joinToString(" | ") { it.substringAfterLast('/') } + " | ..."
    }
} ?: "unknown"
val currentGitChangedFileDetails = currentGitChangedFilePaths?.joinToString("\n") { path ->
    val modifiedMs = rootProject.file(path).takeIf { it.isFile }?.lastModified()?.takeIf { it > 0L } ?: 0L
    "$path\t$modifiedMs"
}.orEmpty()
val currentGitLatestFileModifiedMs = readGitLines(
    "ls-files",
    "--cached",
    "--others",
    "--exclude-standard",
).orEmpty()
    .mapNotNull { path ->
        rootProject.file(path).takeIf { it.isFile }?.lastModified()?.takeIf { it > 0L }
    }
    .maxOrNull() ?: 0L

android {
    namespace = "com.kangle.kardleaf"
    compileSdk = 34
    flavorDimensions += "distribution"

    defaultConfig {
        applicationId = "com.kardleaf"
        minSdk = 23
        targetSdk = 34
        versionCode = 190
        versionName = "1.9.0"
        manifestPlaceholders["appLabel"] = "KardLeaf"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "KARDLEAF_TRIAL_GATEWAY_URL", buildConfigString("https://ai.waikrfaio.xyz"))
        buildConfigField("String", "KARDLEAF_GIT_WORKTREE", buildConfigString(currentGitWorktree))
        buildConfigField("String", "KARDLEAF_GIT_BRANCH", buildConfigString(currentGitBranch))
        buildConfigField("long", "KARDLEAF_LATEST_FILE_MODIFIED_MS", currentGitLatestFileModifiedMs.toString())
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    productFlavors {
        create("stable") {
            dimension = "distribution"
            applicationId = "com.kardleaf"
            manifestPlaceholders["appLabel"] = "KardLeaf"
            buildConfigField("boolean", "KARDLEAF_DEV_VARIANT", "false")
            buildConfigField("String", "KARDLEAF_GIT_COMMIT", buildConfigString(currentGitCommit))
            buildConfigField("String", "KARDLEAF_GIT_MESSAGE", buildConfigString(currentGitMessage))
            buildConfigField("String", "KARDLEAF_GIT_CHANGED_FILES", buildConfigString(currentGitChangedFiles))
            buildConfigField("String", "KARDLEAF_GIT_CHANGED_FILE_DETAILS", buildConfigString(currentGitChangedFileDetails))
        }
        create("dev") {
            dimension = "distribution"
            applicationId = "com.kardleaf.dev"
            versionNameSuffix = "-dev"
            manifestPlaceholders["appLabel"] = "KardLeaf Dev"
            buildConfigField("boolean", "KARDLEAF_DEV_VARIANT", "true")
            buildConfigField("String", "KARDLEAF_GIT_COMMIT", buildConfigString(currentGitCommit))
            buildConfigField("String", "KARDLEAF_GIT_MESSAGE", buildConfigString(currentGitMessage))
            buildConfigField("String", "KARDLEAF_GIT_CHANGED_FILES", buildConfigString(currentGitChangedFiles))
            buildConfigField("String", "KARDLEAF_GIT_CHANGED_FILE_DETAILS", buildConfigString(currentGitChangedFileDetails))
        }
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }
        create("release") {
            storeFile = rootProject.file(localProperties.getProperty("RELEASE_STORE_FILE") ?: "notes.jks")
            storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
            keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "KardLeaf Debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        disable.add("MissingTranslation")
    }
}

androidComponents {
    beforeVariants(selector().withName("stableRelease")) { variant: ApplicationVariantBuilder ->
        variant.isMinifyEnabled = true
        variant.shrinkResources = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.google.code.gson:gson:2.10.1") // Correct group id just in case
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("net.dankito.readability4j:readability4j:1.0.8")
    implementation("com.vladsch.flexmark:flexmark-html2md-converter:0.64.8")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:editor:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:image:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")

    // Koin (DI for Quillpad editor: viewModel/inject)
    implementation("io.insert-koin:koin-android:3.5.3")

    // Navigation Component (Quillpad EditorFragment uses navArgs/findNavController + safe-args)
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // kotlinx.serialization (Quillpad data models use @Serializable)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // RecyclerView (Quillpad editor attachments/tasks recyclers)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
}
