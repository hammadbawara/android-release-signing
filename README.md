# Android Release Signing Plugin

[![Maven Central](https://img.shields.io/maven-central/v/io.github.hammadbawara.android.release-signing/release-signing-plugin?style=flat-square&color=blue)](https://central.sonatype.com/artifact/io.github.hammadbawara.android.release-signing/release-signing-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg?style=flat-square)](LICENSE)

A lightweight Gradle convention plugin for Android that eliminates signing boilerplate. Automatically wires up and validates release signing for both **local development** and **CI/CD (GitHub Actions)** with zero friction for debug builds.

---

## ⚡ Features

- 🔒 **Secure by Default** — Keystores and passwords are never committed to version control.
- 🚀 **Zero Debug Friction** — IDE sync, debug builds, and unit tests run without any keystore configuration.
- ⏱️ **Lazy Release Validation** — Validates credentials *only* when running release tasks (`assembleRelease`, `bundleRelease`).
- 🤖 **Native CI/CD & GitHub Actions** — Resolves environment variables and automatically decodes Base64 keystores on-the-fly.
- 📦 **F-Droid & CI Unsigned Builds** — Build unsigned release APKs seamlessly for F-Droid packaging or pull request verification without secrets.
- 🛡️ **Cross-Platform** — Works seamlessly across Linux, macOS, and Windows.

---

## 📦 Installation

### 1. Add Maven Central
Ensure `mavenCentral()` is in your root `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

### 2. Apply the Plugin

#### Using Version Catalog (Recommended)

In `gradle/libs.versions.toml`:
```toml
[plugins]
android-release-signing = { id = "io.github.hammadbawara.android.release-signing", version = "1.1.0" }
```

In your application module `app/build.gradle.kts`:
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
    id("io.github.hammadbawara.android.release-signing") version "1.1.0"
}
```
</details>

---

## 🛠️ Configuration

Choose the configuration that matches your environment:

<details>
<summary><b>💻 Local Development Setup (Click to expand)</b></summary>

<br>

Add signing credentials to your project's root `local.properties` (make sure `local.properties` is in `.gitignore`):

```properties
RELEASE_STORE_FILE=keystore/release.jks
RELEASE_STORE_PASSWORD=your-keystore-password
RELEASE_KEY_ALIAS=your-key-alias
RELEASE_KEY_PASSWORD=your-key-password
```

#### Property Reference

| Property | Required | Description | Example |
| :--- | :---: | :--- | :--- |
| `RELEASE_STORE_FILE` | Yes | Path to keystore file (relative to project root or absolute) | `keystore/release.jks` |
| `RELEASE_STORE_PASSWORD` | Yes | Keystore access password | `myStorePass123` |
| `RELEASE_KEY_ALIAS` | Yes | Release key alias | `my-release-key` |
| `RELEASE_KEY_PASSWORD` | Yes | Key password | `myKeyPass123` |

</details>

<details>
<summary><b>🤖 GitHub Actions & CI/CD Setup (Click to expand)</b></summary>

<br>

The plugin natively supports environment variables and automatically decodes Base64 keystores on-the-fly into an isolated intermediate file. **No custom bash decoding scripts or `local.properties` files required!**

#### 1. Base64 Encode Your Keystore
Run the command for your operating system to get the Base64 string of your keystore:

- **Linux**:
  ```bash
  base64 -w 0 release.jks > keystore_base64.txt
  ```
- **macOS**:
  ```bash
  base64 -i release.jks -o keystore_base64.txt
  ```
- **Windows (PowerShell)**:
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard
  ```

#### 2. Add GitHub Repository Secrets
In your GitHub repository: **Settings** $\rightarrow$ **Secrets and variables** $\rightarrow$ **Actions**:

| Secret Name | Description |
| :--- | :--- |
| `RELEASE_KEYSTORE_BASE64` | The complete Base64 keystore string |
| `RELEASE_STORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias |
| `RELEASE_KEY_PASSWORD` | Key password |

#### 3. GitHub Actions Workflow
Add `.github/workflows/release.yml` to your repository:

```yaml
name: Build & Sign Release

on:
  push:
    tags:
      - 'v*' # Trigger on version tags (e.g., v1.0.0)
  workflow_dispatch: # Allows manual trigger from GitHub UI

jobs:
  release:
    name: Build Signed Artifacts
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Make gradlew executable
        run: chmod +x gradlew

      # The plugin reads secrets directly from env and decodes the Base64 keystore automatically
      - name: Build Signed Release APK & App Bundle
        env:
          RELEASE_KEYSTORE_BASE64: ${{ secrets.RELEASE_KEYSTORE_BASE64 }}
          RELEASE_STORE_PASSWORD: ${{ secrets.RELEASE_STORE_PASSWORD }}
          RELEASE_KEY_ALIAS: ${{ secrets.RELEASE_KEY_ALIAS }}
          RELEASE_KEY_PASSWORD: ${{ secrets.RELEASE_KEY_PASSWORD }}
        run: ./gradlew assembleRelease bundleRelease --no-daemon

      - name: Upload Signed APK
        uses: actions/upload-artifact@v4
        with:
          name: release-apk
          path: '**/build/outputs/apk/release/*.apk'
          if-no-files-found: error

      - name: Upload Signed AAB
        uses: actions/upload-artifact@v4
        with:
          name: release-aab
          path: '**/build/outputs/bundle/release/*.aab'
          if-no-files-found: error
```

> **Credential Resolution Priority:** Gradle Project Properties (`-P...`) $\rightarrow$ Environment Variables (`env:`) $\rightarrow$ `local.properties`.

</details>

<details>
<summary><b>📦 F-Droid & Unsigned CI Builds Setup (Click to expand)</b></summary>

<br>

In **F-Droid** and **CI environments** (such as Pull Request builds from external contributors where repository secrets are unavailable), you may want release tasks (`assembleRelease`, `bundleRelease`) to produce unsigned artifacts instead of failing fast.

The plugin provides multiple ways to support unsigned builds:

#### Option 1: Command-Line Flag or Environment Variable (Zero Code Changes)
Disable release signing on-demand from the command line or CI pipeline:

```bash
# Explicitly disable release signing
./gradlew assembleRelease -PRELEASE_SIGNING_ENABLED=false

# Or use common alias flags
./gradlew assembleRelease -PdisableReleaseSigning
# or
./gradlew assembleRelease -PunsignedRelease=true
```

Or set environment variables in your build container:
```bash
export RELEASE_SIGNING_ENABLED=false
# or
export DISABLE_RELEASE_SIGNING=true
```

#### Option 2: Optional Signing for F-Droid (`required = false`)
When release signing is optional:
- If credentials **are provided**, the build is signed and validated normally.
- If credentials **are absent**, the build produces an unsigned release artifact (`app-release-unsigned.apk`) without failing.
- If credentials **are partially configured or corrupted**, validation still fails fast to alert you of configuration mistakes.

```bash
# Allow unsigned release when credentials are not configured
./gradlew assembleRelease -PRELEASE_SIGNING_REQUIRED=false

# Or pass F-Droid flag / environment variable
./gradlew assembleRelease -Pfdroid
```

In F-Droid metadata (`.fdroid.yml` or `metadata/package.yml`):
```yaml
Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: v1.0.0
    gradle:
      - yes
    gradleflags:
      - -PRELEASE_SIGNING_ENABLED=false
```

#### Option 3: Configure via Kotlin DSL in `build.gradle.kts`
You can configure signing behavior directly in your application module:

```kotlin
// app/build.gradle.kts
releaseSigning {
    // Completely bypass release signing
    enabled.set(false)

    // OR: Allow unsigned builds whenever credentials are not configured
    // (Ideal for open-source repositories and F-Droid)
    required.set(false)
}
```

#### Configuration Flags Reference

| Property / Flag | Type | Description |
| :--- | :---: | :--- |
| `RELEASE_SIGNING_ENABLED` | Boolean | Enable or disable release signing (`true`/`false`). When `false`, produces unsigned release APKs. |
| `-PdisableReleaseSigning` | Flag | Shorthand to disable release signing. |
| `-PunsignedRelease` | Flag | Shorthand to disable release signing. |
| `RELEASE_SIGNING_REQUIRED` | Boolean | Whether signing credentials are required (`true`/`false`). When `false` and credentials are missing, builds unsigned release. |
| `-PreleaseSigningOptional=true` | Flag | Shorthand to make signing optional when credentials are not provided. |
| `-Pfdroid` / `FDROID=true` | Flag / Env | Shorthand for F-Droid environments to allow unsigned builds. |

</details>

---

## 🏃 Building Your App

- **Debug Builds** (zero signing configuration required):
  ```bash
  ./gradlew assembleDebug
  ```

- **Release Builds** (automatically signed & validated when credentials configured):
  ```bash
  ./gradlew assembleRelease
  # or
  ./gradlew bundleRelease
  ```

- **Unsigned Release Builds** (for F-Droid or CI PR checks):
  ```bash
  ./gradlew assembleRelease -PRELEASE_SIGNING_ENABLED=false
  # or
  ./gradlew assembleRelease -PRELEASE_SIGNING_REQUIRED=false
  ```

---

## 📄 License

```
Copyright 2026 Hammad Zafar Bawara

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
