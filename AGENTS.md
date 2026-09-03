# Android Release Signing Plugin

## Project Overview

`android-release-signing` is a Gradle convention plugin (`io.github.hammadbawara.android.release-signing`) for Android applications that centralizes and automates release signing configuration using credentials stored in `local.properties`.

## Primary Goal

Eliminate repetitive boilerplate signing configuration across Android projects and application modules. Instead of manually configuring signing configs, keystore file paths, passwords, and aliases in each build script, applying this single convention plugin automatically wires up release signing with security and validation built in.

## Key Features & Design Principles

- **Zero Debug Friction**: Debug builds, unit tests, lint checks, and IDE sync require zero signing configuration.
- **Lazy Release Validation**: Keystores, passwords, and aliases are validated only when an operation actually requires a signed release artifact (e.g. `assembleRelease`, `bundleRelease`).
- **Unsigned Builds for F-Droid & CI**: Release tasks can produce unsigned APKs/bundles on-demand via CLI flags (`-PRELEASE_SIGNING_ENABLED=false`, `-PdisableReleaseSigning`, `-PunsignedRelease=true`), environment variables, or Gradle extension DSL (`releaseSigning { enabled.set(false) }`).
- **Optional Signing Mode**: Supports `releaseSigning { required.set(false) }`, `-PRELEASE_SIGNING_REQUIRED=false`, or `-Pfdroid`, which signs if credentials are provided and builds unsigned if missing, while still failing fast on corrupt/partial configurations.
- **Security First**: Keystore and key passwords are never printed or logged in build outputs, errors, or stack traces.
- **Cross-Platform Path Resolution**: Resolves relative keystore paths against the root project directory, with full support for Linux, macOS, and Windows path conventions.
- **Permanent Sample Project**: The repository includes `my-app` as a permanent sample application used for manual verification and automated Gradle TestKit functional tests.

## Repository Layout

- `android-build-logic/`: The Gradle convention plugin implementation (`com.hammadbawara.android.releasesigning`).
- `my-app/`: Permanent sample Android application demonstrating plugin integration.
- `gradle/`: Shared Gradle wrapper and version catalog (`libs.versions.toml`).
