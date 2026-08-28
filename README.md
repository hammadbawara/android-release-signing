# Android Release Signing Plugin

A lightweight Gradle convention plugin for Android that centralizes and automates release signing using credentials stored in `local.properties`.

---

## Installation

### 1. Enable Maven Central in Plugin Repositories

In your root `settings.gradle.kts`, ensure `mavenCentral()` is in `pluginManagement.repositories`:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

### 2. Apply the Plugin

#### Option A: Direct Plugin ID
In your Android application module's `build.gradle.kts`:

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    id("io.github.hammadbawara.android.release-signing") version "1.0.0"
}
```

#### Option B: Using Version Catalogs (`libs.versions.toml`)
```toml
# gradle/libs.versions.toml
[plugins]
android-release-signing = { id = "io.github.hammadbawara.android.release-signing", version = "1.0.0" }
```

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.release.signing)
}
```

---

## Configuration

Add the release signing properties to your root `local.properties` (make sure `local.properties` is in `.gitignore`):

```properties
RELEASE_STORE_FILE=keystore/release.jks
RELEASE_STORE_PASSWORD=your-keystore-password
RELEASE_KEY_ALIAS=your-key-alias
RELEASE_KEY_PASSWORD=your-key-password
```

### Configuration Reference

| Property | Required | Description | Example |
| :--- | :---: | :--- | :--- |
| `RELEASE_STORE_FILE` | Yes | Path to `.jks` / `.keystore` / `.p12` file (relative to project root or absolute) | `keystore/release.jks` |
| `RELEASE_STORE_PASSWORD` | Yes | Keystore access password | `myStorePass123` |
| `RELEASE_KEY_ALIAS` | Yes | Alias of the release signing key | `my-release-key` |
| `RELEASE_KEY_PASSWORD` | Yes | Password for the signing key | `myKeyPass123` |

---

## Usage

- **Debug Builds**: Zero configuration required. Builds run without signing errors even if `local.properties` or keystores are absent:
  ```bash
  ./gradlew assembleDebug
  ```

- **Release Builds**: Automatically validates keystore existence, verifies credentials, and signs the release APK/AAB:
  ```bash
  ./gradlew assembleRelease
  # or
  ./gradlew bundleRelease
  ```

---

## License

This project is licensed under the [Apache 2.0 License](LICENSE).
