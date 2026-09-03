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
 * using credentials from `local.properties`.
 *
 * Validation is performed lazily and release-only:
 * - Debug builds require zero signing configuration.
 * - Release operations fail fast with actionable, secure diagnostic messages.
 */
class ReleaseSigningPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.plugins.withId("com.android.application") {
            configureReleaseSigning(project)
        }
    }

    private fun configureReleaseSigning(project: Project) {
        val rootLocalProps = project.rootProject.file(ReleaseSigningConstants.LOCAL_PROPERTIES_FILE)
        val projectLocalProps = project.file(ReleaseSigningConstants.LOCAL_PROPERTIES_FILE)
        val localPropsFile = if (projectLocalProps.exists() && !rootLocalProps.exists()) projectLocalProps else rootLocalProps

        val properties = loadLocalProperties(localPropsFile)

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

        // Configure release signing config in Android DSL
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

        // Attach signing config to release build type
        androidExtension.buildTypes.named("release").configure {
            signingConfig = releaseSigning
        }

        // Lazy validation for release variants via Android Components API
        val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
        androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
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
