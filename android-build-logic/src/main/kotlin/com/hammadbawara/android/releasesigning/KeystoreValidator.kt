package com.hammadbawara.android.releasesigning

import org.gradle.api.GradleException
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.UnrecoverableKeyException
import java.util.Base64

/**
 * Validates Android release signing properties, keystore files, and credentials.
 * Ensures security: passwords are NEVER included in error messages or logs.
 */
object KeystoreValidator {

    /**
     * Resolves a keystore path that may be relative, absolute (Unix/Windows), or user-home prefixed.
     * Relative paths are resolved against [rootDir].
     */
    fun resolveKeystoreFile(rawPath: String, rootDir: File): File {
        val trimmed = rawPath.trim()
        val expanded = if (trimmed.startsWith("~" + File.separator) || trimmed.startsWith("~/") || trimmed.startsWith("~\\")) {
            System.getProperty("user.home") + trimmed.substring(1)
        } else {
            trimmed
        }

        // Support Unix absolute (/...) and Windows absolute (C:\..., C:/..., \\unc\...)
        val isWindowsDriveAbsolute = expanded.matches(Regex("^[a-zA-Z]:[\\\\/].*"))
        val isUncPath = expanded.startsWith("\\\\") || expanded.startsWith("//")
        val candidate = File(expanded)

        if (candidate.isAbsolute || isWindowsDriveAbsolute || isUncPath) {
            return candidate.normalize()
        }

        // Relative path: normalize path separators across platforms
        val normalizedRelative = expanded.replace('\\', File.separatorChar).replace('/', File.separatorChar)
        return File(rootDir, normalizedRelative).normalize()
    }

    /**
     * Decodes Base64 encoded keystore content into [destinationFile].
     * Never prints or leaks secret content in error messages.
     */
    fun decodeBase64Keystore(base64Content: String, destinationFile: File): File {
        val sanitized = base64Content.filterNot { it.isWhitespace() }
        val decodedBytes = try {
            Base64.getDecoder().decode(sanitized)
        } catch (_: IllegalArgumentException) {
            throw GradleException(
                buildString {
                    appendLine()
                    appendLine("Release signing configuration is invalid.")
                    appendLine()
                    appendLine("Failed to decode Base64 keystore data (${ReleaseSigningConstants.PROP_KEYSTORE_BASE64}).")
                    appendLine("The provided string is not in valid Base64 encoded format.")
                    appendLine()
                    append("Do not commit keystores or raw secrets to version control.")
                }
            )
        }

        destinationFile.parentFile?.mkdirs()
        destinationFile.writeBytes(decodedBytes)
        return destinationFile
    }

    /**
     * Checks if any release signing property has been configured.
     * Useful for distinguishing between completely unconfigured builds vs partially configured builds.
     */
    fun hasAnyCredentials(
        rawStoreFile: String?,
        storeFileBase64: String?,
        storePassword: String?,
        keyAlias: String?,
        keyPassword: String?
    ): Boolean {
        return !rawStoreFile.isNullOrBlank() ||
            !storeFileBase64.isNullOrBlank() ||
            !storePassword.isNullOrBlank() ||
            !keyAlias.isNullOrBlank() ||
            !keyPassword.isNullOrBlank()
    }

    /**
     * Performs comprehensive validation of release signing parameters.
     * Supports either raw file path or Base64 encoded keystore data.
     *
     * @return the resolved keystore [File] if valid.
     * @throws GradleException with actionable guidance if any validation fails.
     */
    fun validate(
        localPropertiesFile: File?,
        rawStoreFile: String?,
        storePassword: String?,
        keyAlias: String?,
        keyPassword: String?,
        rootDir: File,
        storeFileBase64: String? = null,
        targetKeystoreFile: File? = null
    ): File {
        val missingProperties = mutableListOf<String>()

        val hasStoreFile = !rawStoreFile.isNullOrBlank()
        val hasStoreBase64 = !storeFileBase64.isNullOrBlank()

        if (!hasStoreFile && !hasStoreBase64) {
            missingProperties.add(ReleaseSigningConstants.PROP_STORE_FILE)
        }
        if (storePassword.isNullOrBlank()) {
            missingProperties.add(ReleaseSigningConstants.PROP_STORE_PASSWORD)
        }
        if (keyAlias.isNullOrBlank()) {
            missingProperties.add(ReleaseSigningConstants.PROP_KEY_ALIAS)
        }
        if (keyPassword.isNullOrBlank()) {
            missingProperties.add(ReleaseSigningConstants.PROP_KEY_PASSWORD)
        }

        if (missingProperties.isNotEmpty()) {
            val localPropsPath = localPropertiesFile?.absolutePath
                ?: File(rootDir, ReleaseSigningConstants.LOCAL_PROPERTIES_FILE).absolutePath

            val errorMessage = buildMissingPropertiesErrorMessage(
                missingProperties = missingProperties,
                localPropertiesPath = localPropsPath,
                localPropertiesExists = localPropertiesFile?.exists() == true
            )
            throw GradleException(errorMessage)
        }

        // Determine and resolve keystore file
        val resolvedKeystoreFile: File = when {
            hasStoreBase64 -> {
                val dest = targetKeystoreFile
                    ?: if (hasStoreFile) resolveKeystoreFile(rawStoreFile!!, rootDir)
                    else File(rootDir, "build/${ReleaseSigningConstants.INTERMEDIATE_KEYSTORE_PATH}").normalize()
                decodeBase64Keystore(storeFileBase64!!, dest)
            }
            else -> {
                resolveKeystoreFile(rawStoreFile!!, rootDir)
            }
        }

        if (!resolvedKeystoreFile.exists() || !resolvedKeystoreFile.isFile) {
            throw GradleException(buildKeystoreNotFoundErrorMessage(resolvedKeystoreFile))
        }

        // Validate keystore opening and credentials
        validateKeystoreCredentials(
            keystoreFile = resolvedKeystoreFile,
            storePassword = storePassword!!,
            keyAlias = keyAlias!!,
            keyPassword = keyPassword!!
        )

        return resolvedKeystoreFile
    }

    /**
     * Attempts to open the keystore, verify the alias exists, and verify the key password.
     */
    private fun validateKeystoreCredentials(
        keystoreFile: File,
        storePassword: String,
        keyAlias: String,
        keyPassword: String
    ) {
        val keyStoreTypes = listOf(
            KeyStore.getDefaultType(),
            "PKCS12",
            "JKS"
        ).distinct()

        var keyStore: KeyStore? = null

        for (type in keyStoreTypes) {
            try {
                val ks = KeyStore.getInstance(type)
                FileInputStream(keystoreFile).use { stream ->
                    ks.load(stream, storePassword.toCharArray())
                }
                keyStore = ks
                break
            } catch (_: Exception) {
            }
        }

        if (keyStore == null) {
            throw GradleException(
                buildInvalidKeystorePasswordErrorMessage(keystoreFile)
            )
        }

        // Check if the alias exists in the keystore
        val aliasExists: Boolean
        try {
            aliasExists = keyStore.containsAlias(keyAlias)
        } catch (_: KeyStoreException) {
            throw GradleException(
                buildInvalidKeystorePasswordErrorMessage(keystoreFile)
            )
        }

        if (!aliasExists) {
            throw GradleException(
                buildAliasNotFoundErrorMessage(keystoreFile, keyAlias)
            )
        }

        // Verify key password
        try {
            val key = keyStore.getKey(keyAlias, keyPassword.toCharArray())
            if (key == null) {
                throw GradleException(
                    buildInvalidKeyPasswordErrorMessage(keystoreFile, keyAlias)
                )
            }
        } catch (_: UnrecoverableKeyException) {
            throw GradleException(
                buildInvalidKeyPasswordErrorMessage(keystoreFile, keyAlias)
            )
        } catch (_: NoSuchAlgorithmException) {
            throw GradleException(
                buildInvalidKeyPasswordErrorMessage(keystoreFile, keyAlias)
            )
        } catch (_: KeyStoreException) {
            throw GradleException(
                buildInvalidKeyPasswordErrorMessage(keystoreFile, keyAlias)
            )
        }
    }

    private fun buildMissingPropertiesErrorMessage(
        missingProperties: List<String>,
        localPropertiesPath: String,
        localPropertiesExists: Boolean
    ): String = buildString {
        appendLine()
        appendLine("Release signing configuration is incomplete.")
        appendLine()
        if (!localPropertiesExists) {
            appendLine("The release build requires a signing key, but local.properties was not found.")
            appendLine()
            appendLine("The following required signing properties are missing:")
        } else if (missingProperties.size == 1) {
            appendLine("The release build requires a signing key, but the following property is missing:")
        } else {
            appendLine("The release build requires a signing key, but the following properties are missing:")
        }

        for (prop in missingProperties) {
            appendLine("  - $prop")
        }

        appendLine()
        appendLine(if (localPropertiesExists) "Add them to:" else "Create local.properties at:")
        appendLine("  $localPropertiesPath")
        appendLine()
        appendLine()
        appendLine("Example for local development:")
        if (missingProperties.contains(ReleaseSigningConstants.PROP_STORE_FILE)) {
            appendLine("  ${ReleaseSigningConstants.PROP_STORE_FILE}=/path/to/keystore.jks")
        }
        if (missingProperties.contains(ReleaseSigningConstants.PROP_STORE_PASSWORD)) {
            appendLine("  ${ReleaseSigningConstants.PROP_STORE_PASSWORD}=your-store-password")
        }
        if (missingProperties.contains(ReleaseSigningConstants.PROP_KEY_ALIAS)) {
            appendLine("  ${ReleaseSigningConstants.PROP_KEY_ALIAS}=your-key-alias")
        }
        if (missingProperties.contains(ReleaseSigningConstants.PROP_KEY_PASSWORD)) {
            appendLine("  ${ReleaseSigningConstants.PROP_KEY_PASSWORD}=your-key-password")
        }
        appendLine()
        appendLine("Or for CI/CD (GitHub Actions):")
        appendLine("  Set as environment variables (env:) or Gradle project properties (-P...):")
        if (missingProperties.contains(ReleaseSigningConstants.PROP_STORE_FILE)) {
            appendLine("    RELEASE_KEYSTORE_BASE64 (Base64 encoded keystore) or RELEASE_STORE_FILE")
        }
        if (missingProperties.contains(ReleaseSigningConstants.PROP_STORE_PASSWORD)) {
            appendLine("    RELEASE_STORE_PASSWORD=your-store-password")
        }
        if (missingProperties.contains(ReleaseSigningConstants.PROP_KEY_ALIAS)) {
            appendLine("    RELEASE_KEY_ALIAS=your-key-alias")
        }
        if (missingProperties.contains(ReleaseSigningConstants.PROP_KEY_PASSWORD)) {
            appendLine("    RELEASE_KEY_PASSWORD=your-key-password")
        }
        appendLine()
        appendLine("For F-Droid or unsigned CI builds, disable signing or make it optional:")
        appendLine("  - CLI property: ./gradlew assembleRelease -P${ReleaseSigningConstants.PROP_SIGNING_ENABLED}=false")
        appendLine("  - Or allow unsigned builds: ./gradlew assembleRelease -P${ReleaseSigningConstants.PROP_SIGNING_REQUIRED}=false")
        appendLine("  - Or in build.gradle.kts: releaseSigning { required.set(false) }")
        appendLine()
        append("Do not commit local.properties or your keystore to version control.")
    }

    private fun buildKeystoreNotFoundErrorMessage(keystoreFile: File): String = buildString {
        appendLine()
        appendLine("Release signing configuration is invalid.")
        appendLine()
        appendLine("RELEASE_STORE_FILE points to a keystore that does not exist:")
        appendLine("  ${keystoreFile.path}")
        appendLine()
        appendLine("Check the path in local.properties and make sure the keystore exists.")
        append("Do not commit your keystore to version control.")
    }

    private fun buildInvalidKeystorePasswordErrorMessage(keystoreFile: File): String = buildString {
        appendLine()
        appendLine("Release signing configuration is invalid.")
        appendLine()
        appendLine("Failed to open keystore:")
        appendLine("  ${keystoreFile.path}")
        appendLine()
        appendLine("Reason: Keystore password appears to be incorrect or the keystore file is corrupted.")
        appendLine()
        appendLine("Check ${ReleaseSigningConstants.PROP_STORE_PASSWORD} in local.properties and verify your keystore.")
        append("Do not commit local.properties or your keystore to version control.")
    }

    private fun buildAliasNotFoundErrorMessage(keystoreFile: File, alias: String): String = buildString {
        appendLine()
        appendLine("Release signing configuration is invalid.")
        appendLine()
        appendLine("The key alias does not exist in the keystore:")
        appendLine("  ${ReleaseSigningConstants.PROP_KEY_ALIAS}: $alias")
        appendLine()
        appendLine("Keystore file:")
        appendLine("  ${keystoreFile.path}")
        appendLine()
        appendLine("Verify the key alias in local.properties.")
        append("Do not commit local.properties or your keystore to version control.")
    }

    private fun buildInvalidKeyPasswordErrorMessage(keystoreFile: File, alias: String): String = buildString {
        appendLine()
        appendLine("Release signing configuration is invalid.")
        appendLine()
        appendLine("Failed to access key with alias '$alias' in keystore:")
        appendLine("  ${keystoreFile.path}")
        appendLine()
        appendLine("Reason: Key password appears to be incorrect.")
        appendLine()
        appendLine("Check ${ReleaseSigningConstants.PROP_KEY_PASSWORD} in local.properties.")
        append("Do not commit local.properties or your keystore to version control.")
    }
}
