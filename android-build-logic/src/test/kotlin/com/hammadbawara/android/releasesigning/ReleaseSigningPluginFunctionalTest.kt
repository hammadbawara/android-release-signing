package com.hammadbawara.android.releasesigning

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ReleaseSigningPluginFunctionalTest {

    @TempDir
    lateinit var testProjectDir: File

    private lateinit var buildLogicDir: File
    private lateinit var sampleAppDir: File

    private val validStorePassword = "test_store_password_123"
    private val validKeyAlias = "test_release_key"
    private val validKeyPassword = "test_key_password_456"

    @BeforeEach
    fun setup() {
        val userDir = File(System.getProperty("user.dir"))
        buildLogicDir = if (userDir.name == "android-build-logic") userDir else File(userDir, "android-build-logic")
        val rootDir = if (userDir.name == "android-build-logic") userDir.parentFile else userDir
        sampleAppDir = File(rootDir, "my-app")

        check(sampleAppDir.exists() && sampleAppDir.isDirectory) {
            "Permanent sample project 'my-app' not found at: ${sampleAppDir.absolutePath}"
        }

        // Copy the permanent sample app structure to the isolated temp directory
        copySampleProject(sampleAppDir, testProjectDir)

        // Ensure settings.gradle.kts points to the absolute path of android-build-logic
        val settingsFile = File(testProjectDir, "settings.gradle.kts")
        val updatedSettings = settingsFile.readText()
            .replace(Regex("""includeBuild\s*\(\s*["'][^"']+["']\s*\)"""), "includeBuild(\"${buildLogicDir.absolutePath}\")")
        settingsFile.writeText(updatedSettings)

        // Clean any pre-existing local.properties or keystores for test isolation
        File(testProjectDir, "local.properties").delete()
        File(testProjectDir, "keystore").deleteRecursively()
        File(testProjectDir, "keystores").deleteRecursively()
        File(testProjectDir, "keys").deleteRecursively()
    }

    private fun copySampleProject(source: File, target: File) {
        source.walkTopDown()
            .filter { file ->
                val relPath = file.relativeTo(source).path.replace('\\', '/')
                !relPath.startsWith(".gradle") &&
                !relPath.startsWith(".kotlin") &&
                relPath != "build" && !relPath.startsWith("build/") &&
                relPath != "app/build" && !relPath.startsWith("app/build/") &&
                relPath != "app/.gradle" && !relPath.startsWith("app/.gradle/") &&
                !relPath.endsWith("local.properties") &&
                !relPath.endsWith(".jks") &&
                !relPath.endsWith(".keystore")
            }
            .forEach { file ->
                val relativePath = file.relativeTo(source).path
                val destination = File(target, relativePath)
                if (file.isDirectory) {
                    destination.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.copyTo(destination, overwrite = true)
                }
            }
    }

    private fun createKeystore(
        file: File,
        storePassword: String = validStorePassword,
        keyAlias: String = validKeyAlias,
        keyPassword: String = validKeyPassword
    ) {
        file.parentFile?.mkdirs()
        val keytoolCmd = arrayOf(
            "keytool",
            "-genkeypair",
            "-alias", keyAlias,
            "-keypass", keyPassword,
            "-keystore", file.absolutePath,
            "-storepass", storePassword,
            "-storetype", "JKS",
            "-dname", "CN=Functional Test, OU=Android, O=Organization, C=US",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "365"
        )
        val process = ProcessBuilder(*keytoolCmd).redirectErrorStream(true).start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val output = process.inputStream.bufferedReader().readText()
            throw IllegalStateException("Failed to create test keystore: $output")
        }
    }

    private fun runner(vararg args: String): GradleRunner {
        return GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(*args)
            .forwardOutput()
    }

    @Test
    fun `test 1 debug build succeeds without local properties`() {
        File(testProjectDir, "local.properties").delete()

        val result = runner(":app:assembleDebug").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":app:assembleDebug")?.outcome)
    }

    @Test
    fun `test 2 debug build succeeds with incomplete signing configuration`() {
        File(testProjectDir, "local.properties").writeText(
            """
            RELEASE_KEY_ALIAS=only_alias_configured
            """.trimIndent()
        )

        val result = runner(":app:assembleDebug").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":app:assembleDebug")?.outcome)
    }

    @Test
    fun `test 3 release build fails when local properties is missing`() {
        File(testProjectDir, "local.properties").delete()

        val result = runner(":app:assembleRelease").buildAndFail()
        val output = result.output

        assertTrue(output.contains("Release signing configuration is incomplete."))
        assertTrue(output.contains("local.properties was not found"))
        assertTrue(output.contains("RELEASE_STORE_FILE"))
        assertTrue(output.contains("RELEASE_STORE_PASSWORD"))
        assertTrue(output.contains("RELEASE_KEY_ALIAS"))
        assertTrue(output.contains("RELEASE_KEY_PASSWORD"))
        assertTrue(output.contains("Do not commit local.properties or your keystore to version control."))
    }

    @Test
    fun `test 4 release build reports all missing properties in single error`() {
        File(testProjectDir, "local.properties").writeText(
            """
            RELEASE_STORE_PASSWORD=some_password
            """.trimIndent()
        )

        val result = runner(":app:assembleRelease").buildAndFail()
        val output = result.output

        assertTrue(output.contains("Release signing configuration is incomplete."))
        assertTrue(output.contains("RELEASE_STORE_FILE"))
        assertTrue(output.contains("RELEASE_KEY_ALIAS"))
        assertTrue(output.contains("RELEASE_KEY_PASSWORD"))
        assertFalse(output.contains("- RELEASE_STORE_PASSWORD"))
    }

    @Test
    fun `test 5 release build fails when keystore file does not exist`() {
        val nonExistentKeystore = File(testProjectDir, "missing/release.jks").absolutePath
        File(testProjectDir, "local.properties").writeText(
            """
            RELEASE_STORE_FILE=$nonExistentKeystore
            RELEASE_STORE_PASSWORD=$validStorePassword
            RELEASE_KEY_ALIAS=$validKeyAlias
            RELEASE_KEY_PASSWORD=$validKeyPassword
            """.trimIndent()
        )

        val result = runner(":app:assembleRelease").buildAndFail()
        val output = result.output

        assertTrue(output.contains("Release signing configuration is invalid."))
        assertTrue(output.contains("RELEASE_STORE_FILE points to a keystore that does not exist"))
        assertTrue(output.contains("missing/release.jks") || output.contains(nonExistentKeystore))
    }

    @Test
    fun `test 6 release build succeeds with valid signing configuration`() {
        val keystoreFile = File(testProjectDir, "keystore/release.jks")
        createKeystore(keystoreFile)

        File(testProjectDir, "local.properties").writeText(
            """
            RELEASE_STORE_FILE=${keystoreFile.absolutePath}
            RELEASE_STORE_PASSWORD=$validStorePassword
            RELEASE_KEY_ALIAS=$validKeyAlias
            RELEASE_KEY_PASSWORD=$validKeyPassword
            """.trimIndent()
        )

        val result = runner(":app:assembleRelease").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":app:assembleRelease")?.outcome)
    }

    @Test
    fun `test 7 passwords never appear in error messages`() {
        val secretStorePwd = "SUPER_SECRET_STORE_PASSWORD_99999"
        val secretKeyPwd = "SUPER_SECRET_KEY_PASSWORD_88888"
        val nonExistentKeystore = File(testProjectDir, "nonexistent.jks").absolutePath

        File(testProjectDir, "local.properties").writeText(
            """
            RELEASE_STORE_FILE=$nonExistentKeystore
            RELEASE_STORE_PASSWORD=$secretStorePwd
            RELEASE_KEY_ALIAS=$validKeyAlias
            RELEASE_KEY_PASSWORD=$secretKeyPwd
            """.trimIndent()
        )

        val result = runner(":app:assembleRelease").buildAndFail()
        val output = result.output

        assertFalse(output.contains(secretStorePwd), "Output must not contain store password!")
        assertFalse(output.contains(secretKeyPwd), "Output must not contain key password!")
    }

    @Test
    fun `test 8 windows style keystore paths work`() {
        val keystoreFile = File(testProjectDir, "keystores/release.jks")
        createKeystore(keystoreFile)

        // Windows relative path with backslashes
        File(testProjectDir, "local.properties").writeText(
            """
            RELEASE_STORE_FILE=keystores\\release.jks
            RELEASE_STORE_PASSWORD=$validStorePassword
            RELEASE_KEY_ALIAS=$validKeyAlias
            RELEASE_KEY_PASSWORD=$validKeyPassword
            """.trimIndent()
        )

        val result = runner(":app:assembleRelease").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":app:assembleRelease")?.outcome)
    }

    @Test
    fun `test 9 unix style keystore paths work`() {
        val keystoreFile = File(testProjectDir, "keystores/release.jks")
        createKeystore(keystoreFile)

        // Unix relative path with forward slashes
        File(testProjectDir, "local.properties").writeText(
            """
            RELEASE_STORE_FILE=keystores/release.jks
            RELEASE_STORE_PASSWORD=$validStorePassword
            RELEASE_KEY_ALIAS=$validKeyAlias
            RELEASE_KEY_PASSWORD=$validKeyPassword
            """.trimIndent()
        )

        val result = runner(":app:assembleRelease").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":app:assembleRelease")?.outcome)
    }

    @Test
    fun `test 10 relative keystore paths are resolved correctly relative to root`() {
        val keystoreFile = File(testProjectDir, "keys/my-release.jks")
        createKeystore(keystoreFile)

        File(testProjectDir, "local.properties").writeText(
            """
            RELEASE_STORE_FILE=keys/my-release.jks
            RELEASE_STORE_PASSWORD=$validStorePassword
            RELEASE_KEY_ALIAS=$validKeyAlias
            RELEASE_KEY_PASSWORD=$validKeyPassword
            """.trimIndent()
        )

        val result = runner(":app:assembleRelease").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":app:assembleRelease")?.outcome)
    }
}
