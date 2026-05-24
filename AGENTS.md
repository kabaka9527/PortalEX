# Repository Guidelines

## Project Structure & Module Organization

PortalEX is an Android virtual positioning project built around LSPosed/Xposed hooks. The Gradle project has four modules:

- `app/`: Android app, XML UI, Baidu map integration, services, resources, and SDK assets in `app/libs/`.
- `xposed/`: Xposed module code, location/NMEA hook implementations, and C++ Dobby hooks in `xposed/src/main/cpp/`.
- `nmea/`: JVM library for parsing NMEA sentences; keep NMEA handling here instead of ad hoc string parsing.
- `system-api/`: Android framework API stubs used by hook code.

Tests live in module `src/test/` and `src/androidTest/` trees. AOSP references are in `refs/`.

## Build, Test, and Development Commands

Use the checked-in Gradle wrapper:

```bash
./gradlew assembleAppRelease   # Build release APKs for app flavors
./gradlew assembleArm64Release # Build arm64 release APK
./gradlew assembleX64Release   # Build x86_64 release APK
./gradlew test                 # Run JVM unit tests for all modules
```

Hook behavior requires LSPosed on device. Baidu Maps needs an API key outside source control. Release signing uses `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.

## Coding Style & Naming Conventions

Write Kotlin and Java for JVM target 17. Follow Android XML + viewBinding style; do not add Jetpack Compose unless the project is intentionally migrated. Use the Gradle version catalog (`gradle/libs.versions.toml`) instead of inline dependency coordinates. Keep hooks consistent with `BaseLocationHook` and shared mock state utilities such as `FakeLoc`.

Use `PascalCase` for classes, `camelCase` for functions and properties, and descriptive XML resource names such as `fragment_route_mock.xml`. Chinese comments are acceptable for location-specific behavior.

## Testing Guidelines

Unit tests use JUnit 4 and must live under each module's `src/test/` tree; do not place unit tests in `src/androidTest/`. Android instrumentation tests use AndroidX JUnit and Espresso and are reserved for UI flows or device integration. Name JVM tests after the behavior under test and place them near the owning module, for example `nmea/src/test/java/...`.

New or changed production logic must include unit tests that keep line and branch coverage at or above 98% for the affected module. If a path cannot be unit-tested because it depends on LSPosed, Android framework internals, native hooks, or device-only behavior, document the reason in the PR and add the closest feasible JVM test around pure parsing, state, or command-building logic. Run `./gradlew test` before submitting hook, parser, or ViewModel changes; run connected Android tests for UI flows or device integration.

## Commit & Pull Request Guidelines

Recent history uses short Chinese imperative summaries, for example `修复...`, `提高...`, and `清理...`. Keep subjects concise and behavior-focused. Pull requests should describe the affected module, visible behavior, test evidence, and LSPosed/device/Android-version assumptions. Include screenshots for UI changes and note requirements such as Baidu API keys or signing variables.

## Context Maintenance

Every code, build, workflow, configuration, or documentation change must update the repository context log before handoff. Keep context logs under `docs/context/` with one file per calendar day, named `YYYY-MM-DD.md`.

For each work session, append a short entry to the current day's file with:

- the user-visible goal or bug being handled;
- the modules and files changed;
- important design decisions, assumptions, and device/LSPosed/Android-version constraints;
- validation performed, including commands, device logs, CI runs, or why validation could not run;
- follow-up risks or next checks.

If a change is purely mechanical and does not affect behavior, still add a brief note to the current day's context file. Do not create multiple files for the same date; append to the existing daily file.

## Architecture & Configuration Notes

Coordinate handling uses Baidu/GCJ02 internally and WGS84 for external I/O. Avoid adding diagnostic network calls to build scripts. Do not commit local IDE files, generated APKs, keystores, API keys, or crash-reporting secrets.
