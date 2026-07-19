# Fix KSP build error "unexpected jvm signature V"

The project is failing to build with the error `[ksp] java.lang.IllegalStateException: unexpected jvm signature V`. This error is a known compatibility issue between older versions of Room (specifically Room 2.6.x) and KSP 2.0+ (used with Kotlin 2.0+).

## Proposed Changes

### [Component Name] Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/User/AndroidStudioProjects/SimpleSudoku/gradle/libs.versions.toml)
Update Room version to `2.8.4` (the latest stable version as of July 2026) to ensure compatibility with KSP 2.0.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:kspDebugKotlin` to verify the KSP error is resolved.
- Run a full build: `./gradlew assembleDebug`.

### Manual Verification
- None required beyond confirming the build succeeds.
