# APP MODULE

**Generated:** 2026-05-24
**Path:** `app/` — Main Android application

## OVERVIEW

Android app UI + services for PortalEX virtual positioning. XML-based layout, viewBinding, Navigation Component, Baidu Maps SDK, Bugly crash reporting. Package: `moe.fuqiuluo.portal`.

## STRUCTURE

```
app/src/main/java/moe/fuqiuluo/portal/
├── Portal.kt              # Application: init Baidu Maps SDK + Bugly
├── MainActivity.kt        # Baidu map, permissions, search, drawer nav
├── android/               # Android-specific utilities
│   ├── Bugly.kt
│   ├── coro/              # Coroutine controllers
│   ├── permission/        # RequestPermissions helper
│   ├── rom/               # ROM detection
│   ├── root/              # Shell/root utils
│   ├── widget/            # RockerView, SatelliteRadar, DeveloperView
│   └── window/            # Overlay permissions
├── bdmap/                 # Baidu POI data classes & conversion
├── ext/                   # Kotlin extensions (coord, overlay, prefs, per-module)
├── service/               # MockServiceHelper (IPC to xposed)
└── ui/
    ├── gnss/              # GNSS mock fragment
    ├── home/              # Home fragment
    ├── mock/              # Mock, Rocker, Route fragments + history
    ├── notification/      # Notification channels
    ├── settings/          # Settings fragment
    └── viewmodel/         # ViewModels (BaiduMap, Home, MockService, Mock, Settings)
```

## WHERE TO LOOK

| Task | File |
|------|------|
| Add UI toggle | `settings/SettingsFragment.kt` + `ext/Perfs.kt` (preference) |
| Add fragment | `ui/<name>/` + nav graph + viewModel |
| Map interaction | `MainActivity.kt` + `ui/viewmodel/BaiduMapViewModel.kt` |
| IPC to xposed module | `service/MockServiceHelper.kt` |
| Start/stop mock | `ui/viewmodel/MockServiceViewModel.kt` |

## CONVENTIONS

- XML layouts with **viewBinding** (`ActivityMainBinding`, etc.)
- Fragment + ViewModel via `activityViewModels<>()` or `ViewModelProvider`
- Preferences via `Context` extension properties in `ext/Perfs.kt`
- Coord conversion: `ext/Loc.kt` (gcj02 ↔ wgs84)
- Build flavors: `app` (arm64+x64), `arm64` (arm64 only), `x64` (x64 only)
- Compile SDK 35, minSdk 26

## COMMANDS

```bash
./gradlew :app:assembleAppRelease
./gradlew :app:assembleArm64Release
./gradlew :app:assembleX64Release
```
