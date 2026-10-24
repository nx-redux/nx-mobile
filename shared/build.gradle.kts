import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    android {
        namespace = "com.nxredux.mobile.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        // Compose resources (fonts, strings) are packaged as Android resources/assets; the KMP library plugin leaves them off.
        androidResources { enable = true }
        withHostTest { }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.coil.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.nxredux.mobile.shared.res"
    generateResClass = auto
}

// MiSans (© Xiaomi) is used under Xiaomi's terms (https://hyperos.mi.com/font/en/download/): software may embed it and
// must note that it does (https://hyperos.mi.com/font/en/faq/; Settings → System → About and Licenses), but the font files may not be
// distributed on their own, so the TTF is not in git.
// fetchMiSans takes MiSansLatin-Semibold.ttf (every UI text) and fetchMiSansRegular MiSansLatin-Regular.ttf (the settings
// rows' descriptions) from Xiaomi's MiSans_Latin.zip (about 3.5 MB, read as a stream, never stored; the full MiSans.zip
// is 230 MB for CJK glyphs the app doesn't bundle, other scripts use the system font) once per machine, caches each in
// the Gradle user home, checks its SHA-256 and copies it to where Compose resources expect it (gitignored).
// -PmisansTtf=<path> and -PmisansRegularTtf=<path> take local copies instead (offline builds). A new Xiaomi release
// fails the check: review its terms, then update the hashes (and the cache key in .github/workflows/release.yaml).
abstract class FetchMiSans : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val entry: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:Internal abstract val cacheDir: DirectoryProperty
    @get:Optional @get:InputFile abstract val localCopy: RegularFileProperty
    @get:OutputFile abstract val target: RegularFileProperty

    private fun sha256Of(file: File): String = MessageDigest.getInstance("SHA-256").let { md ->
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }

    // Xiaomi's server sometimes cuts the stream short (EOFException mid-zip), so a dropped download is retried.
    private fun download(dest: File) {
        val tmp = File(dest.parentFile, dest.name + ".part")
        dest.parentFile.mkdirs()
        for (attempt in 1..ATTEMPTS) {
            logger.lifecycle("fetchMiSans: downloading ${url.get()} (once per machine, attempt $attempt of $ATTEMPTS)")
            try {
                val conn = URI(url.get()).toURL().openConnection().apply { connectTimeout = 30_000; readTimeout = 60_000 }
                ZipInputStream(conn.getInputStream().buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: throw GradleException("fetchMiSans: ${entry.get()} not found in ${url.get()}")
                        if (e.name == entry.get()) { tmp.outputStream().use { zip.copyTo(it) }; break }
                    }
                }
                break
            } catch (e: java.io.IOException) {
                tmp.delete()
                if (attempt == ATTEMPTS) throw GradleException("fetchMiSans: download failed after $ATTEMPTS attempts", e)
                logger.warn("fetchMiSans: attempt $attempt failed ($e), retrying")
                Thread.sleep(5_000L * attempt)
            }
        }
        if (!tmp.renameTo(dest)) throw GradleException("fetchMiSans: could not move $tmp to $dest")
    }

    private companion object { const val ATTEMPTS = 4 }

    @TaskAction fun fetch() {
        val want = sha256.get()
        val cached = cacheDir.get().asFile.resolve("${entry.get().substringAfterLast('/').removeSuffix(".ttf")}-${want.take(12)}.ttf")
        val source = localCopy.orNull?.asFile ?: cached.also { if (!it.isFile || sha256Of(it) != want) download(it) }
        val got = sha256Of(source)
        if (got != want) {
            if (source == cached) cached.delete()
            throw GradleException("fetchMiSans: $source has SHA-256 $got, expected $want (see shared/build.gradle.kts)")
        }
        source.copyTo(target.get().asFile, overwrite = true)
    }
}

fun registerFetchMiSans(name: String, style: String, hash: String, property: String, resource: String) =
    tasks.register<FetchMiSans>(name) {
        url.set("https://hyperos.mi.com/font-download/MiSans_Latin.zip")
        entry.set("MiSans Latin/ttf/MiSansLatin-$style.ttf")
        sha256.set(hash)
        cacheDir.set(File(gradle.gradleUserHomeDir, "nx-mobile/misans"))
        providers.gradleProperty(property).orNull?.let { localCopy.set(file(it)) }
        target.set(layout.projectDirectory.file("src/commonMain/composeResources/font/$resource.ttf"))
    }
val fetchMiSans = registerFetchMiSans(
    "fetchMiSans", "Semibold", "2080f37e783eb269129c15f7f40028943fe659c1859efc5ba5f0de4e726c0e62", "misansTtf", "misans_semibold",
)
val fetchMiSansRegular = registerFetchMiSans(
    "fetchMiSansRegular", "Regular", "d6afa946f09bf876e58ec2f784988a68647af9edb77709643f00fa25f183135b", "misansRegularTtf", "misans_regular",
)
// Every Compose resources task reads the font directory (value copies, accessors, preparation, the Android assets copy).
tasks.matching {
    it.name != fetchMiSans.name && it.name != fetchMiSansRegular.name &&
        listOf("ComposeRes", "ValueResources", "ResourceAccessors", "ResourceCollectors").any { part -> it.name.contains(part) }
}.configureEach { dependsOn(fetchMiSans, fetchMiSansRegular) }

sqldelight {
    databases {
        create("GameTimeDb") {
            packageName.set("com.nxredux.mobile.gametime.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
