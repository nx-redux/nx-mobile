import java.security.MessageDigest
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Build-time secrets, env first, then local.properties (gitignored), else "" (that source is off): the ScreenScraper
// developer credentials (TheGamesDB takes only the user's own key, entered in the app; the app ships none). Values are
// never printed; they only reach BuildConfig, XOR-encoded (nx-redux's gen-ss-credentials.sh, done here): a `byte[]`
// <NAME>_ENC plus its key <NAME>_KEY, decoded at run time by ScraperSecret, so the plain text is not in the dex for
// `strings` or jadx to find. Obfuscation against casual extraction, not secrecy: the key ships beside it. The key is
// SHA-256 of a fixed salt, the field name and the value, so it differs per field and per value yet is the same on every
// build of the same inputs (BuildConfig, the configuration cache and the build cache stay stable; a random key would
// rebuild BuildConfig on every configuration).
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(env: String, prop: String): String = System.getenv(env)?.ifEmpty { null } ?: localProps.getProperty(prop) ?: ""
fun buildConfigString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
fun javaBytes(bytes: ByteArray): String = bytes.joinToString(",", "new byte[]{", "}") { it.toString() }
fun encodedSecret(name: String, value: String): Pair<String, String> {
    val plain = value.toByteArray(Charsets.UTF_8)
    val key = MessageDigest.getInstance("SHA-256").digest("nx-mobile/$name/$value".toByteArray(Charsets.UTF_8))
    val enc = ByteArray(plain.size) { i -> (plain[i].toInt() xor key[i % key.size].toInt()).toByte() }
    return javaBytes(enc) to javaBytes(if (plain.isEmpty()) ByteArray(0) else key)
}

// Settings -> System's About Build and Released rows: the short commit ("-dirty" with uncommitted changes) and its committer
// date (ISO-8601), never the build time. providers.exec keeps the configuration cache valid; no git: "unknown" / "".
fun git(vararg args: String): String? = runCatching {
    val run = providers.exec {
        commandLine("git", *args)
        workingDir = rootDir
        isIgnoreExitValue = true
    }
    run.standardOutput.asText.get().trim().takeIf { run.result.get().exitValue == 0 }
}.getOrNull()
val gitCommit: String = git("rev-parse", "--short", "HEAD")?.ifEmpty { null }
    ?.let { commit -> if (git("status", "--porcelain").isNullOrEmpty()) commit else "$commit-dirty" } ?: "unknown"
val gitCommitDate: String = git("log", "-1", "--format=%cI") ?: ""

// The release version: -PnxVersionName=<major.minor.patch> (the release workflow passes its tag without the v), else
// (a local build) the latest vX.Y.Z tag reachable from HEAD without the v, else 0.1.0 (no tags, or no git). versionCode
// follows it, major * 1_000_000 + minor * 1_000 + patch, so every release installs over the last.
val gitTagVersion: String? = git("describe", "--tags", "--abbrev=0", "--match", "v[0-9]*.[0-9]*.[0-9]*")
    ?.removePrefix("v")?.takeIf { Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}""").matches(it) }
val nxVersionName: String = (findProperty("nxVersionName") as String?)?.trim()?.ifEmpty { null } ?: gitTagVersion ?: "0.1.0"
val nxVersionCode: Int = Regex("""(\d{1,3})\.(\d{1,3})\.(\d{1,3})""").matchEntire(nxVersionName)
    ?.destructured?.let { (major, minor, patch) -> major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt() }
    ?: throw GradleException("nxVersionName must be major.minor.patch (each 0-999), got '$nxVersionName'")

// Release signing, env first, then local.properties, like the secrets above: ANDROID_KEYSTORE_FILE / release.storeFile,
// ANDROID_KEYSTORE_PASSWORD / release.storePassword, ANDROID_KEY_ALIAS / release.keyAlias, ANDROID_KEY_PASSWORD /
// release.keyPassword. Without a store file the release build stays unsigned. Values are never printed.
val releaseStoreFile: String = secret("ANDROID_KEYSTORE_FILE", "release.storeFile")

// The in-app updater's feed (BuildConfig.UPDATE_FEED): the latest GitHub release. Only a debug build can point elsewhere,
// -PnxUpdateFeed=<url> (a local server for testing the update flow); a release build always asks GitHub.
val githubUpdateFeed = "https://api.github.com/repos/nx-redux/nx-mobile/releases/latest"

android {
    namespace = "com.nxredux.mobile"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.nxredux.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = nxVersionCode
        versionName = nxVersionName
        ndk { abiFilters += listOf("arm64-v8a") }
        buildConfigField("String", "GIT_COMMIT", buildConfigString(gitCommit))
        buildConfigField("String", "COMMIT_DATE", buildConfigString(gitCommitDate))
        for ((name, value) in listOf(
            "SS_DEV_ID" to secret("SCREENSCRAPER_DEV_ID", "screenscraper.devId"),
            "SS_DEV_PASSWORD" to secret("SCREENSCRAPER_DEV_PASSWORD", "screenscraper.devPassword"),
        )) {
            val (enc, key) = encodedSecret(name, value)
            buildConfigField("byte[]", "${name}_ENC", enc)
            buildConfigField("byte[]", "${name}_KEY", key)
        }
    }
    signingConfigs {
        if (releaseStoreFile.isNotEmpty()) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = secret("ANDROID_KEYSTORE_PASSWORD", "release.storePassword")
                keyAlias = secret("ANDROID_KEY_ALIAS", "release.keyAlias")
                keyPassword = secret("ANDROID_KEY_PASSWORD", "release.keyPassword")
            }
        }
    }
    // github: the build the release workflow ships, which updates itself from GitHub releases. play: for a store listing,
    // which must not download and install APKs itself, so it carries no updater (each flavor's src set holds its side).
    flavorDimensions += "distribution"
    productFlavors {
        create("github") { dimension = "distribution" }
        create("play") { dimension = "distribution" }
    }
    buildTypes {
        // The debug build is "NX Redux Dev" (com.nxredux.mobile.dev, inverted icon in src/debug/res): it installs beside the
        // release app with its own data, so an adb test build never replaces a user's install.
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "UPDATE_FEED", buildConfigString((findProperty("nxUpdateFeed") as String?)?.trim()?.ifEmpty { null } ?: githubUpdateFeed))
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "UPDATE_FEED", buildConfigString(githubUpdateFeed))
            if (releaseStoreFile.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }
    // useLegacyPackaging: native libraries are compressed in the APK and extracted at install, because the bundled rsync
    // (librsync_exec.so, tools/rsync) must run from nativeLibraryDir: Android executes only files it extracted itself.
    // App-wide: every core then loads from the extracted directory (checked on device).
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":libretrodroid"))
    implementation(libs.compose.runtime)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.window)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    testImplementation(libs.junit)
    testImplementation(libs.coil.compose)   // AndroidArtFilesTest: how Coil reads a versioned file:// ref
}

val requiredCores = listOf(
    "libmelondsds_libretro_android.so",
    "libmupen64plus_next_gles3_libretro_android.so",
    "libppsspp_libretro_android.so",
    "libgambatte_libretro_android.so",
    "libmgba_libretro_android.so",
    "libfceumm_libretro_android.so",
    "libsnes9x_libretro_android.so",
    "libgenesis_plus_gx_libretro_android.so",
    "librace_libretro_android.so",
    "libmednafen_pce_fast_libretro_android.so",
    "libmednafen_vb_libretro_android.so",
    "libhandy_libretro_android.so",
    "libstella2014_libretro_android.so",
    "libprosystem_libretro_android.so",
    "liba5200_libretro_android.so",
    "libmednafen_wswan_libretro_android.so",
    "libmednafen_supafaust_libretro_android.so",
    "libpokemini_libretro_android.so",
    "libfake08_libretro_android.so",
    "libprboom_libretro_android.so",
    "libgpsp_libretro_android.so",
    "libpcsx_rearmed_libretro_android.so",
    "libswanstation_libretro_android.so",
    "libpicodrive_libretro_android.so",
    "libgearcoleco_libretro_android.so",
    "libfbneo_libretro_android.so",
    "libflycast_libretro_android.so",
)

val checkCores = tasks.register("checkCores") {
    val dir = layout.projectDirectory.dir("src/main/jniLibs/arm64-v8a").asFile
    val skip = project.hasProperty("skipCoreCheck")
    doLast {
        val missing = requiredCores.filter { !dir.resolve(it).exists() }
        if (missing.isNotEmpty() && !skip) {
            throw GradleException(
                "Missing libretro cores in $dir: $missing\n" +
                    "Build them with: gmake -C cores all   (see cores/README.md), or pass -PskipCoreCheck for a partial dev build."
            )
        }
        if (missing.isNotEmpty()) logger.warn("checkCores: skipping, missing $missing")
        if (!dir.resolve("librsync_exec.so").exists()) {
            logger.warn("checkCores: librsync_exec.so missing; netplay will play without copying saves. Build it with: gmake -C tools/rsync install")
        }
    }
}
tasks.named("preBuild") { dependsOn(checkCores) }

// Licence texts ship with the binaries as assets/licenses/, staged at build time so each file lives in one place:
// - nx-mobile.txt: the app's own GPL-3.0 (/LICENSE), which also covers the LibretroDroid fork and the nx-redux art.
// - third-party-art.md: /THIRD_PARTY_ART.md, the credits and terms of the bundled logos and controller art.
// - every LICENSES/*.txt: the curated notices (FB Neo's licence verbatim; melonDS DS's and flycast's with their
//   dependencies'; the runner's native libraries; Kenney's input prompts).
// - every cores/output/licenses/*.txt: each core's licence texts, copied from its pinned checkout by cores/Makefile
//   (<core>_LICENSE). The release workflow runs gmake -C cores check-licenses so none is missing.
abstract class StageLicenses : DefaultTask() {
    @get:InputFile abstract val appLicense: RegularFileProperty
    @get:InputFile abstract val thirdPartyArt: RegularFileProperty
    @get:InputFiles abstract val curated: ConfigurableFileCollection
    @get:InputFiles abstract val cores: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction fun stage() {
        val dir = outputDir.get().asFile.resolve("licenses")
        dir.deleteRecursively()
        dir.mkdirs()
        appLicense.get().asFile.copyTo(dir.resolve("nx-mobile.txt"))
        thirdPartyArt.get().asFile.copyTo(dir.resolve("third-party-art.md"))
        (curated.files + cores.files).forEach { it.copyTo(dir.resolve(it.name)) }
    }
}

val stageLicenses = tasks.register<StageLicenses>("stageLicenses") {
    val root = rootProject.layout.projectDirectory
    appLicense.set(root.file("LICENSE"))
    thirdPartyArt.set(root.file("THIRD_PARTY_ART.md"))
    curated.from(root.dir("LICENSES").asFileTree.matching { include("*.txt") })
    cores.from(root.dir("cores/output/licenses").asFileTree.matching { include("*.txt") })
    cores.from(root.dir("tools/rsync/output/licenses").asFileTree.matching { include("*.txt") })   // rsync (netplay's save copy)
}

androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(stageLicenses, StageLicenses::outputDir) }
}
