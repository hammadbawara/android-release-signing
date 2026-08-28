# Android Release Signing Plugin

[![Maven Central](https://img.shields.io/maven-central/v/io.github.hammadbawara.android.release-signing/release-signing-plugin?style=flat-square&color=blue)](https://central.sonatype.com/artifact/io.github.hammadbawara.android.release-signing/release-signing-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg?style=flat-square)](LICENSE)

A lightweight Gradle convention plugin for Android that eliminates boilerplate signing configurations. It automatically wires up release signing from credentials in your `local.properties` with zero friction for debug builds.

---

## ⚡ Why Use This?

- 🔒 **Secure by Default** — Keystore paths and passwords stay in `local.properties` (never committed to git).
- 🚀 **Zero Debug Friction** — Debug builds, IDE sync, and CI unit tests run without requiring any keystores or credentials.
- ⏱️ **Lazy Release Validation** — Validates keystore paths, aliases, and credentials *only* when running release tasks (`assembleRelease`, `bundleRelease`).
- 🛡️ **Cross-Platform** — Seamlessly resolves relative keystore paths across macOS, Linux, and Windows.

---

## 🚀 How to Use

### 1. Add Credentials to `local.properties`

Add the following keys to your project's root `local.properties` (make sure `local.properties` is in `.gitignore`):

```properties
RELEASE_STORE_FILE=keystore/release.jks
RELEASE_STORE_PASSWORD=your-keystore-password
RELEASE_KEY_ALIAS=your-key-alias
RELEASE_KEY_PASSWORD=your-key-password
```

| Property | Required | Description | Example |
| :--- | :---: | :--- | :--- |
| `RELEASE_STORE_FILE` | Yes | Path to keystore file (relative to project root or absolute) | `keystore/release.jks` |
| `RELEASE_STORE_PASSWORD` | Yes | Keystore access password | `myStorePass123` |
| `RELEASE_KEY_ALIAS` | Yes | Release key alias | `my-release-key` |
| `RELEASE_KEY_PASSWORD` | Yes | Key password | `myKeyPass123` |

### 2. Build Your App

- **Debug Builds** (no keystore needed):
  ```bash
  ./gradlew assembleDebug
  ```

- **Release Builds** (automatically signed & validated):
  ```bash
  ./gradlew assembleRelease
  # or
  ./gradlew bundleRelease
  ```

---

## 📦 Installation

### Step 1: Add Maven Central Repository

Ensure `mavenCentral()` is present in your root `settings.gradle.kts`:

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

### Step 2: Apply the Plugin

#### Using Version Catalog (Recommended)

In `gradle/libs.versions.toml`:
```toml
[plugins]
android-release-signing = { id = "io.github.hammadbawara.android.release-signing", version = "1.0.0" }
```

In your app module `app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.release.signing)
}
```

<details>
<summary><b>Or direct plugin application</b></summary>

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    id("io.github.hammadbawara.android.release-signing") version "1.0.0"
}
```
</details>

---

## 📄 License

```
Copyright 2026 Hammad Bawara

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
