# Android Release Signing Plugin

## Project Overview

`android-release-signing` is a Gradle convention plugin (`com.hammadbawara.android.release-signing`) for Android applications that centralizes and automates release signing configuration using credentials stored in `local.properties`.

## Primary Goal

Eliminate repetitive boilerplate signing configuration across Android projects and application modules. Instead of manually configuring signing configs, keystore file paths, passwords, and aliases in each build script, applying this single convention plugin automatically wires up release signing with security and validation built in.

## Key Features & Design Principles

- **Zero Debug Friction**: Debug builds, unit tests, lint checks, and IDE sync require zero signing configuration.
- **Lazy Release Validation**: Keystores, passwords, and aliases are validated only when an operation actually requires a signed release artifact (e.g. `assembleRelease`, `bundleRelease`).
- **Security First**: Keystore and key passwords are never printed or logged in build outputs, errors, or stack traces.
- **Cross-Platform Path Resolution**: Resolves relative keystore paths against the root project directory, with full support for Linux, macOS, and Windows path conventions.
- **Permanent Sample Project**: The repository includes `my-app` as a permanent sample application used for manual verification and automated Gradle TestKit functional tests.

## Repository Layout

- `android-build-logic/`: The Gradle convention plugin implementation (`com.hammadbawara.android.releasesigning`).
- `my-app/`: Permanent sample Android application demonstrating plugin integration.
- `gradle/`: Shared Gradle wrapper and version catalog (`libs.versions.toml`).
