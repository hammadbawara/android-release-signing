package com.hammadbawara.android.releasesigning

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class KeystoreValidatorTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var validKeystoreFile: File
    private val validStorePassword = "store_password_123"
    private val validKeyAlias = "my_release_key"
    private val validKeyPassword = "key_password_456"

    @BeforeEach
    fun setup() {
        validKeystoreFile = File(tempDir, "test-keystore.jks")
        createTestKeystore(
            file = validKeystoreFile,
            storePassword = validStorePassword,
            keyAlias = validKeyAlias,
            keyPassword = validKeyPassword
        )
    }

    private fun createTestKeystore(
        file: File,
        storePassword: String,
        keyAlias: String,
        keyPassword: String
    ) {
        val keytoolCmd = arrayOf(
            "keytool",
            "-genkeypair",
            "-alias", keyAlias,
            "-keypass", keyPassword,
            "-keystore", file.absolutePath,
            "-storepass", storePassword,
            "-storetype", "JKS",
            "-dname", "CN=Test Developer, OU=Android, O=Organization, C=US",
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

    @Test
    fun `resolveKeystoreFile resolves relative paths against root directory`() {
        val rootDir = File(tempDir, "project-root")
        rootDir.mkdirs()

        val resolved = KeystoreValidator.resolveKeystoreFile("keystores/release.jks", rootDir)
        assertEquals(File(rootDir, "keystores/release.jks").normalize(), resolved)
    }

    @Test
    fun `resolveKeystoreFile resolves Windows relative paths against root directory`() {
        val rootDir = File(tempDir, "project-root")
        rootDir.mkdirs()

        val resolved = KeystoreValidator.resolveKeystoreFile("keystores\\release.jks", rootDir)
        assertEquals(File(rootDir, "keystores/release.jks").normalize(), resolved)
    }

    @Test
    fun `resolveKeystoreFile handles Unix absolute paths`() {
        val rootDir = File(tempDir, "project-root")
        val absolutePath = "/opt/keys/release.jks"

        val resolved = KeystoreValidator.resolveKeystoreFile(absolutePath, rootDir)
        assertEquals(File(absolutePath).normalize(), resolved)
    }

    @Test
    fun `resolveKeystoreFile handles Windows absolute paths with backslashes`() {
        val rootDir = File(tempDir, "project-root")
        val windowsPath = "C:\\keystores\\release.jks"

        val resolved = KeystoreValidator.resolveKeystoreFile(windowsPath, rootDir)
        assertEquals(File(windowsPath).normalize(), resolved)
    }

    @Test
    fun `resolveKeystoreFile handles Windows absolute paths with forward slashes`() {
        val rootDir = File(tempDir, "project-root")
        val windowsPath = "D:/android/keys/release.jks"

        val resolved = KeystoreValidator.resolveKeystoreFile(windowsPath, rootDir)
        assertEquals(File(windowsPath).normalize(), resolved)
    }

    @Test
    fun `validate succeeds with valid configuration and existing keystore`() {
        val localPropsFile = File(tempDir, "local.properties").apply {
            writeText(
                """
                RELEASE_STORE_FILE=${validKeystoreFile.absolutePath}
                RELEASE_STORE_PASSWORD=$validStorePassword
                RELEASE_KEY_ALIAS=$validKeyAlias
                RELEASE_KEY_PASSWORD=$validKeyPassword
                """.trimIndent()
            )
        }

        val resolvedFile = KeystoreValidator.validate(
            localPropertiesFile = localPropsFile,
            rawStoreFile = validKeystoreFile.absolutePath,
            storePassword = validStorePassword,
            keyAlias = validKeyAlias,
            keyPassword = validKeyPassword,
            rootDir = tempDir
        )

        assertNotNull(resolvedFile)
        assertTrue(resolvedFile.exists())
    }

    @Test
    fun `validate reports all missing properties when local properties does not exist`() {
        val missingLocalProps = File(tempDir, "nonexistent-local.properties")

        val exception = assertThrows<GradleException> {
            KeystoreValidator.validate(
                localPropertiesFile = missingLocalProps,
                rawStoreFile = null,
                storePassword = null,
                keyAlias = null,
                keyPassword = null,
                rootDir = tempDir
            )
        }

        val message = exception.message.orEmpty()
        assertTrue(message.contains("Release signing configuration is incomplete."))
        assertTrue(message.contains("local.properties was not found"))
        assertTrue(message.contains(ReleaseSigningConstants.PROP_STORE_FILE))
        assertTrue(message.contains(ReleaseSigningConstants.PROP_STORE_PASSWORD))
        assertTrue(message.contains(ReleaseSigningConstants.PROP_KEY_ALIAS))
        assertTrue(message.contains(ReleaseSigningConstants.PROP_KEY_PASSWORD))
        assertTrue(message.contains("Do not commit local.properties or your keystore to version control."))
    }

    @Test
    fun `validate reports only the specific missing properties when partial properties provided`() {
        val localPropsFile = File(tempDir, "local.properties").apply {
            writeText("RELEASE_STORE_PASSWORD=some_password\n")
        }

        val exception = assertThrows<GradleException> {
            KeystoreValidator.validate(
                localPropertiesFile = localPropsFile,
                rawStoreFile = null,
                storePassword = "some_password",
                keyAlias = null,
                keyPassword = null,
                rootDir = tempDir
            )
        }

        val message = exception.message.orEmpty()
        assertTrue(message.contains("Release signing configuration is incomplete."))
        assertTrue(message.contains(ReleaseSigningConstants.PROP_STORE_FILE))
        assertTrue(message.contains(ReleaseSigningConstants.PROP_KEY_ALIAS))
        assertTrue(message.contains(ReleaseSigningConstants.PROP_KEY_PASSWORD))
        // RELEASE_STORE_PASSWORD should NOT be listed as missing
        assertFalse(message.contains("- " + ReleaseSigningConstants.PROP_STORE_PASSWORD))
    }

    @Test
    fun `validate fails when keystore file does not exist`() {
        val nonExistentPath = File(tempDir, "missing-keystore.jks").absolutePath
        val localPropsFile = File(tempDir, "local.properties").apply {
            writeText("RELEASE_STORE_FILE=$nonExistentPath\n")
        }

        val exception = assertThrows<GradleException> {
            KeystoreValidator.validate(
                localPropertiesFile = localPropsFile,
                rawStoreFile = nonExistentPath,
                storePassword = validStorePassword,
                keyAlias = validKeyAlias,
                keyPassword = validKeyPassword,
                rootDir = tempDir
            )
        }

        val message = exception.message.orEmpty()
        assertTrue(message.contains("Release signing configuration is invalid."))
        assertTrue(message.contains("RELEASE_STORE_FILE points to a keystore that does not exist"))
        assertTrue(message.contains(nonExistentPath))
    }

    @Test
    fun `validate fails when store password is incorrect and never prints password`() {
        val wrongStorePassword = "SUPER_SECRET_WRONG_PASSWORD_xyz"
        val localPropsFile = File(tempDir, "local.properties")

        val exception = assertThrows<GradleException> {
            KeystoreValidator.validate(
                localPropertiesFile = localPropsFile,
                rawStoreFile = validKeystoreFile.absolutePath,
                storePassword = wrongStorePassword,
                keyAlias = validKeyAlias,
                keyPassword = validKeyPassword,
                rootDir = tempDir
            )
        }

        val message = exception.message.orEmpty()
        assertTrue(message.contains("Failed to open keystore"))
        assertTrue(message.contains("Keystore password appears to be incorrect"))
        // Password must NEVER appear in exception message
        assertFalse(message.contains(wrongStorePassword))
    }

    @Test
    fun `validate fails when key alias does not exist in keystore`() {
        val wrongAlias = "non_existent_alias"
        val localPropsFile = File(tempDir, "local.properties")

        val exception = assertThrows<GradleException> {
            KeystoreValidator.validate(
                localPropertiesFile = localPropsFile,
                rawStoreFile = validKeystoreFile.absolutePath,
                storePassword = validStorePassword,
                keyAlias = wrongAlias,
                keyPassword = validKeyPassword,
                rootDir = tempDir
            )
        }

        val message = exception.message.orEmpty()
        assertTrue(message.contains("The key alias does not exist in the keystore"))
        assertTrue(message.contains(wrongAlias))
    }

    @Test
    fun `validate fails when key password is incorrect and never prints password`() {
        val wrongKeyPassword = "SUPER_SECRET_WRONG_KEY_PWD_999"
        val localPropsFile = File(tempDir, "local.properties")

        val exception = assertThrows<GradleException> {
            KeystoreValidator.validate(
                localPropertiesFile = localPropsFile,
                rawStoreFile = validKeystoreFile.absolutePath,
                storePassword = validStorePassword,
                keyAlias = validKeyAlias,
                keyPassword = wrongKeyPassword,
                rootDir = tempDir
            )
        }

        val message = exception.message.orEmpty()
        assertTrue(message.contains("Failed to access key with alias"))
        assertTrue(message.contains("Key password appears to be incorrect"))
        // Password must NEVER appear in exception message
        assertFalse(message.contains(wrongKeyPassword))
    }
}
