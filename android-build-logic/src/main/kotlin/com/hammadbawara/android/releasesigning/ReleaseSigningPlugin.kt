package com.hammadbawara.android.releasesigning

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.util.Locale
import java.util.Properties

/**
 * Gradle convention plugin that automatically configures Android release signing
 * using credentials from `local.properties`, environment variables, or Gradle project properties.
 *
 * Supports unsigned release builds for F-Droid and CI environments:
 * - Debug builds require zero signing configuration.
 * - Release builds can be explicitly disabled via CLI, environment variable, or DSL extension.
 * - Release signing can be made optional (e.g., for F-Droid/CI), producing unsigned artifacts
 *   when credentials are not provided while continuing to sign when credentials are present.
 * - When release signing is required, release operations fail fast with actionable, secure diagnostic messages.
 */
class ReleaseSigningPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            ReleaseSigningConstants.EXTENSION_NAME,
            ReleaseSigningExtension::class.java
        )

        project.plugins.withId("com.android.application") {
            configureReleaseSigning(project, extension)
        }
    }

    private fun configureReleaseSigning(project: Project, extension: ReleaseSigningExtension) {
        val rootLocalProps = project.rootProject.file(ReleaseSigningConstants.LOCAL_PROPERTIES_FILE)
        val projectLocalProps = project.file(ReleaseSigningConstants.LOCAL_PROPERTIES_FILE)
        val localPropsFile = if (projectLocalProps.exists() && !rootLocalProps.exists()) projectLocalProps else rootLocalProps

        val properties = loadLocalProperties(localPropsFile)

        // Set conventions on the extension from properties/env/local.properties
        val defaultEnabled = resolveBooleanProperty(
            project = project,
            localProperties = properties,
            trueNames = ReleaseSigningConstants.ENABLED_PROPERTY_NAMES,
            falseNames = ReleaseSigningConstants.DISABLED_PROPERTY_NAMES,
            default = true
        )
        val defaultRequired = resolveBooleanProperty(
            project = project,
            localProperties = properties,
            trueNames = ReleaseSigningConstants.REQUIRED_PROPERTY_NAMES,
            falseNames = ReleaseSigningConstants.OPTIONAL_PROPERTY_NAMES,
            default = true
        )

        extension.enabled.convention(defaultEnabled)
        extension.required.convention(defaultRequired)

        // Helper closures that respect explicit command-line/environment overrides
        fun isSigningEnabled(): Boolean {
            val cliOrEnvDisabled = checkExplicitFlag(project, properties, ReleaseSigningConstants.DISABLED_PROPERTY_NAMES)
            if (cliOrEnvDisabled) return false
            val explicitEnabled = checkExplicitBoolean(project, properties, ReleaseSigningConstants.ENABLED_PROPERTY_NAMES)
            if (explicitEnabled != null) return explicitEnabled
            return extension.enabled.get()
        }

        fun isSigningRequired(): Boolean {
            val cliOrEnvOptional = checkExplicitFlag(project, properties, ReleaseSigningConstants.OPTIONAL_PROPERTY_NAMES)
            if (cliOrEnvOptional) return false
            val explicitRequired = checkExplicitBoolean(project, properties, ReleaseSigningConstants.REQUIRED_PROPERTY_NAMES)
            if (explicitRequired != null) return explicitRequired
            return extension.required.get()
        }

        val rawStoreFile = resolveProperty(ReleaseSigningConstants.PROP_STORE_FILE, project, properties)
        val storeFileBase64 = resolveProperty(
            ReleaseSigningConstants.PROP_KEYSTORE_BASE64,
            project,
            properties,
            fallbackNames = listOf(ReleaseSigningConstants.PROP_STORE_FILE_BASE64)
        )
        val storePassword = resolveProperty(ReleaseSigningConstants.PROP_STORE_PASSWORD, project, properties)
        val keyAlias = resolveProperty(ReleaseSigningConstants.PROP_KEY_ALIAS, project, properties)
        val keyPassword = resolveProperty(ReleaseSigningConstants.PROP_KEY_PASSWORD, project, properties)

        val hasAnyCredentials = KeystoreValidator.hasAnyCredentials(
            rawStoreFile = rawStoreFile,
            storeFileBase64 = storeFileBase64,
            storePassword = storePassword,
            keyAlias = keyAlias,
            keyPassword = keyPassword
        )

        val intermediateKeystoreFile = File(
            project.rootDir,
            "build/${ReleaseSigningConstants.INTERMEDIATE_KEYSTORE_PATH}"
        ).normalize()

        val resolvedStoreFile = when {
            !rawStoreFile.isNullOrBlank() -> {
                val resolved = KeystoreValidator.resolveKeystoreFile(rawStoreFile, project.rootDir)
                if (!storeFileBase64.isNullOrBlank()) {
                    try {
                        KeystoreValidator.decodeBase64Keystore(storeFileBase64, resolved)
                    } catch (_: Exception) {
                    }
                }
                resolved
            }
            !storeFileBase64.isNullOrBlank() -> {
                try {
                    KeystoreValidator.decodeBase64Keystore(storeFileBase64, intermediateKeystoreFile)
                } catch (_: Exception) {
                }
                intermediateKeystoreFile
            }
            else -> {
                null
            }
        }

        val androidExtension = project.extensions.getByType(ApplicationExtension::class.java)
        val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)

        // Configure release signing config in Android DSL lazily after project script evaluation
        androidComponents.finalizeDsl {
            val enabled = isSigningEnabled()
            val required = isSigningRequired()

            if (!enabled) {
                project.logger.lifecycle(
                    "[release-signing] Release signing is disabled. Building unsigned release artifacts."
                )
                return@finalizeDsl
            }

            if (!hasAnyCredentials && !required) {
                project.logger.lifecycle(
                    "[release-signing] Release signing credentials not found and signing is not required. Building unsigned release artifacts."
                )
                return@finalizeDsl
            }

            // Either credentials are provided, or signing is strictly required (which will fail fast in validation task)
            val releaseSigning = androidExtension.signingConfigs.maybeCreate(ReleaseSigningConstants.SIGNING_CONFIG_NAME)
            if (resolvedStoreFile != null) {
                releaseSigning.storeFile = resolvedStoreFile
            }
            if (!storePassword.isNullOrBlank()) {
                releaseSigning.storePassword = storePassword
            }
            if (!keyAlias.isNullOrBlank()) {
                releaseSigning.keyAlias = keyAlias
            }
            if (!keyPassword.isNullOrBlank()) {
                releaseSigning.keyPassword = keyPassword
            }

            androidExtension.buildTypes.named("release").configure {
                signingConfig = releaseSigning
            }
        }

        // Lazy validation for release variants via Android Components API
        androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
            val enabled = isSigningEnabled()
            val required = isSigningRequired()

            // If signing is disabled, or no credentials were configured and signing is optional:
            // Skip registering the validation task to allow building unsigned release APKs / AABs.
            if (!enabled || (!hasAnyCredentials && !required)) {
                return@onVariants
            }

            val variantName = variant.name
            val capitalizedVariantName = variantName.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
            }

            val validateTaskProvider = project.tasks.register(
                "validateReleaseSigningFor$capitalizedVariantName",
                ValidateReleaseSigningTask::class.java
            )
            validateTaskProvider.configure {
                val task = this
                task.variantName.set(variantName)
                task.rootDirPath.set(project.rootDir.absolutePath)
                task.localPropertiesPath.set(localPropsFile.absolutePath)

                if (rawStoreFile != null) {
                    task.storeFilePath.set(rawStoreFile)
                }
                if (storeFileBase64 != null) {
                    task.storeFileBase64.set(storeFileBase64)
                    task.targetKeystorePath.set(
                        resolvedStoreFile?.absolutePath ?: intermediateKeystoreFile.absolutePath
                    )
                }
                if (storePassword != null) {
                    task.storePassword.set(storePassword)
                }
                if (keyAlias != null) {
                    task.keyAlias.set(keyAlias)
                }
                if (keyPassword != null) {
                    task.keyPassword.set(keyPassword)
                }
            }

            // Hook task dependencies so that validation runs before any packaging or signing task
            project.tasks.matching { task ->
                val name = task.name
                name.equals("validateSigning$capitalizedVariantName", ignoreCase = true) ||
                name.equals("package$capitalizedVariantName", ignoreCase = true) ||
                name.equals("sign${capitalizedVariantName}Bundle", ignoreCase = true) ||
                name.equals("package${capitalizedVariantName}Bundle", ignoreCase = true) ||
                name.equals("bundle$capitalizedVariantName", ignoreCase = true) ||
                name.equals("assemble$capitalizedVariantName", ignoreCase = true)
            }.configureEach {
                dependsOn(validateTaskProvider)
                mustRunAfter(validateTaskProvider)
            }
        }
    }

    private fun checkExplicitFlag(project: Project, localProperties: Properties, names: List<String>): Boolean {
        for (name in names) {
            val projectProp = project.findProperty(name)?.toString()?.trim()
            if (projectProp != null) {
                if (projectProp.isEmpty() || projectProp.equals("true", ignoreCase = true) || projectProp == "1") {
                    return true
                }
            }
            val env = System.getenv(name)?.trim()
            if (!env.isNullOrBlank()) {
                if (env.equals("true", ignoreCase = true) || env == "1") {
                    return true
                }
            }
            val local = localProperties.getProperty(name)?.trim()
            if (!local.isNullOrBlank()) {
                if (local.equals("true", ignoreCase = true) || local == "1") {
                    return true
                }
            }
        }
        return false
    }

    private fun checkExplicitBoolean(project: Project, localProperties: Properties, names: List<String>): Boolean? {
        for (name in names) {
            val projectProp = project.findProperty(name)?.toString()?.trim()
            if (!projectProp.isNullOrBlank()) {
                if (projectProp.equals("false", ignoreCase = true) || projectProp == "0") return false
                if (projectProp.equals("true", ignoreCase = true) || projectProp == "1") return true
            }
            val env = System.getenv(name)?.trim()
            if (!env.isNullOrBlank()) {
                if (env.equals("false", ignoreCase = true) || env == "0") return false
                if (env.equals("true", ignoreCase = true) || env == "1") return true
            }
            val local = localProperties.getProperty(name)?.trim()
            if (!local.isNullOrBlank()) {
                if (local.equals("false", ignoreCase = true) || local == "0") return false
                if (local.equals("true", ignoreCase = true) || local == "1") return true
            }
        }
        return null
    }

    private fun resolveBooleanProperty(
        project: Project,
        localProperties: Properties,
        trueNames: List<String>,
        falseNames: List<String>,
        default: Boolean
    ): Boolean {
        if (checkExplicitFlag(project, localProperties, falseNames)) {
            return false
        }
        val explicitVal = checkExplicitBoolean(project, localProperties, trueNames)
        if (explicitVal != null) {
            return explicitVal
        }
        return default
    }

    private fun resolveProperty(
        name: String,
        project: Project,
        localProperties: Properties,
        fallbackNames: List<String> = emptyList()
    ): String? {
        val allNames = listOf(name) + fallbackNames
        for (propName in allNames) {
            val projectProp = project.findProperty(propName)?.toString()?.trim()
            if (!projectProp.isNullOrBlank()) {
                return projectProp
            }
            val envVal = System.getenv(propName)?.trim()
            if (!envVal.isNullOrBlank()) {
                return envVal
            }
            val localVal = localProperties.getProperty(propName)?.trim()
            if (!localVal.isNullOrBlank()) {
                return localVal
            }
        }
        return null
    }

    companion object {
        fun loadLocalProperties(file: File): Properties {
            val properties = Properties()
            if (!file.exists()) return properties

            file.forEachLine(Charsets.UTF_8) { rawLine ->
                val trimmed = rawLine.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("!")) {
                    val separatorIndex = trimmed.indexOf('=')
                    if (separatorIndex != -1) {
                        val key = trimmed.substring(0, separatorIndex).trim()
                        val value = trimmed.substring(separatorIndex + 1).trim()
                        properties.setProperty(key, value)
                    }
                }
            }
            return properties
        }
    }
}
