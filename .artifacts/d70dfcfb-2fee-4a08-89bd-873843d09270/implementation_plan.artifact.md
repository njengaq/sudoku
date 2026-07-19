# Implementation Plan - Fix KSP "unexpected jvm signature V" Error

The project is currently failing to build with the error `[ksp] java.lang.IllegalStateException: unexpected jvm signature V`. This is a known compatibility issue between Room 2.6.x and KSP 2.x (used with Kotlin 2.x). Room 2.6.x does not correctly handle `suspend` functions returning `Unit` (JVM signature `V`) when processed by KSP 2.x.

The resolution is to upgrade Room to a version that supports KSP 2.x, which is Room 2.7.0 or higher. Based on version lookup, Room 2.8.4 is the latest stable version.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/User/AndroidStudioProjects/SimpleSudoku/gradle/libs.versions.toml)
- Update `room` version from `2.6.1` to `2.8.4`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:kspDebugKotlin` to verify that the KSP processing now completes successfully.
- Run a full build: `./gradlew assembleDebug`.

### Manual Verification
- None required as this is a build-time fix.
