package com.hammadbawara.android.releasesigning

import java.io.File

/**
 * Constants and data models for Android release signing configuration.
 */
object ReleaseSigningConstants {
    const val PROP_STORE_FILE = "RELEASE_STORE_FILE"
    const val PROP_KEYSTORE_BASE64 = "RELEASE_KEYSTORE_BASE64"
    const val PROP_STORE_FILE_BASE64 = "RELEASE_STORE_FILE_BASE64"
    const val PROP_STORE_PASSWORD = "RELEASE_STORE_PASSWORD"
    const val PROP_KEY_ALIAS = "RELEASE_KEY_ALIAS"
    const val PROP_KEY_PASSWORD = "RELEASE_KEY_PASSWORD"

    val REQUIRED_PROPERTIES = listOf(
        PROP_STORE_FILE,
        PROP_STORE_PASSWORD,
        PROP_KEY_ALIAS,
        PROP_KEY_PASSWORD
    )

    const val SIGNING_CONFIG_NAME = "release"
    const val LOCAL_PROPERTIES_FILE = "local.properties"
    const val INTERMEDIATE_KEYSTORE_PATH = "intermediates/release-signing/release.keystore"
}

/**
 * Encapsulates the resolved release signing properties.
 */
data class ReleaseSigningConfig(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String
)
