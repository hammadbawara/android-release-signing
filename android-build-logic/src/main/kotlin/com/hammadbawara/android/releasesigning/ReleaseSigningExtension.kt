package com.hammadbawara.android.releasesigning

import org.gradle.api.provider.Property

/**
 * Gradle DSL extension for configuring release signing behavior.
 *
 * Example usage in `build.gradle.kts`:
 * ```kotlin
 * releaseSigning {
 *     // Completely bypass release signing (produces unsigned release APK/AAB)
 *     enabled.set(false)
 *
 *     // Or allow unsigned release builds when credentials are not configured (e.g. for F-Droid/CI)
 *     required.set(false)
 * }
 * ```
 */
abstract class ReleaseSigningExtension {

    /**
     * Whether release signing is enabled.
     *
     * When `false`, release builds will not be configured with a signingConfig,
     * producing unsigned artifacts (e.g., `app-release-unsigned.apk` or `app-release.aab`).
     *
     * Defaults to `true`. Can also be controlled via:
     * - Gradle properties: `-PRELEASE_SIGNING_ENABLED=false`, `-PreleaseSigningEnabled=false`,
     *   `-PdisableReleaseSigning`, `-PunsignedRelease=true`
     * - Environment variables: `RELEASE_SIGNING_ENABLED=false`, `DISABLE_RELEASE_SIGNING=true`
     * - `local.properties`: `RELEASE_SIGNING_ENABLED=false`
     */
    abstract val enabled: Property<Boolean>

    /**
     * Whether release signing credentials are strictly required when signing is enabled.
     *
     * When `false`:
     * - If credentials are completely missing, release builds proceed without signing (unsigned).
     * - If credentials are provided, they are validated and used to sign the release build.
     * - If credentials are provided but invalid/incomplete, validation still fails fast to prevent mistakes.
     *
     * Defaults to `true`. Can also be controlled via:
     * - Gradle properties: `-PRELEASE_SIGNING_REQUIRED=false`, `-PreleaseSigningRequired=false`,
     *   `-PsigningRequired=false`, `-PreleaseSigningOptional=true`, `-Pfdroid`
     * - Environment variables: `RELEASE_SIGNING_REQUIRED=false`, `RELEASE_SIGNING_OPTIONAL=true`, `FDROID=true`
     * - `local.properties`: `RELEASE_SIGNING_REQUIRED=false`
     */
    abstract val required: Property<Boolean>
}
