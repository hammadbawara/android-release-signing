package com.hammadbawara.android.releasesigning

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task that validates Android release signing credentials and keystore files.
 * Designed to execute lazily only when release signing is required.
 */
abstract class ValidateReleaseSigningTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val storeFilePath: Property<String>

    @get:Input
    @get:Optional
    abstract val storeFileBase64: Property<String>

    @get:Input
    @get:Optional
    abstract val targetKeystorePath: Property<String>

    @get:Input
    @get:Optional
    abstract val storePassword: Property<String>

    @get:Input
    @get:Optional
    abstract val keyAlias: Property<String>

    @get:Input
    @get:Optional
    abstract val keyPassword: Property<String>

    @get:Input
    abstract val rootDirPath: Property<String>

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    @get:Optional
    abstract val localPropertiesPath: Property<String>

    init {
        group = "verification"
        description = "Validates release signing configuration for release variants."
    }

    @TaskAction
    fun validate() {
        val rootDir = File(rootDirPath.get())
        val localPropsFile = localPropertiesPath.orNull?.let { File(it) }
        val targetFile = targetKeystorePath.orNull?.let { File(it) }

        KeystoreValidator.validate(
            localPropertiesFile = localPropsFile,
            rawStoreFile = storeFilePath.orNull,
            storePassword = storePassword.orNull,
            keyAlias = keyAlias.orNull,
            keyPassword = keyPassword.orNull,
            rootDir = rootDir,
            storeFileBase64 = storeFileBase64.orNull,
            targetKeystoreFile = targetFile
        )

        logger.lifecycle("Release signing configuration validated successfully for variant '${variantName.get()}'.")
    }
}
