# XPOSED MODULE

**Generated:** 2026-05-24
**Path:** `xposed/` — LSPosed hook module for location services

## OVERVIEW

Xposed module that hooks Android framework location services to inject fake GPS/Location/NMEA data. Uses Xposed API + C++ Dobby native hooks. Package: `moe.fuqiuluo.xposed`.

## STRUCTURE

```
xposed/src/main/java/moe/fuqiuluo/xposed/
├── BaseDivineService.kt        # Base class for hook services
├── BaseLocationHook.kt         # Core: injectLocation(), injectNMEA()
├── FakeLocation.kt             # Hook entry point
├── RemoteCommandHandler.kt     # IPC command handler (app ↔ xposed)
├── dobby/Dobby.kt              # C++ Dobby native hook bindings
├── hooks/
│   ├── BasicLocationHook.kt
│   ├── LocationManagerHook.kt
│   ├── LocationServiceHook.kt
│   ├── blindhook/              # BlindHook, BlindHookLocation
│   ├── fused/                  # AndroidFusedLocationProviderHook, ThirdPartyLocationHook
│   ├── gnss/GnssHook.kt
│   ├── miui/                   # MiuiBlurLocationProviderHook, MiuiLocationManagerHook
│   ├── nmea/LocationNMEAHook.kt
│   ├── oplus/OplusLocationHook.kt
│   ├── provider/LocationProviderManagerHook.kt
│   ├── sensor/SystemSensorManagerHook.kt
│   ├── telephony/              # BaseTelephonyHook, TelephonyHook, miui/MiuiTelephonyManagerHook
│   └── wlan/WlanHook.kt
└── utils/
    ├── BinderUtils.kt          # Binder IPC helpers
    ├── FakeLoc.kt              # Global mock configuration singleton
    ├── Logger.kt               # Logging utility
    └── Xposed.kt               # Xposed helper extensions
```

## WHERE TO LOOK

| Task | File |
|------|------|
| Add new hook | `hooks/<target>/` — subclass BaseDivineService or BaseLocationHook |
| Location injection logic | `BaseLocationHook.kt` — `injectLocation()`, `injectNMEA()` |
| Mock config state | `utils/FakeLoc.kt` — all toggleable parameters |
| IPC commands | `RemoteCommandHandler.kt` — command→action dispatch |
| Entry point | `FakeLocation.kt` — Xposed init |

## CONVENTIONS

- **Hook hierarchy**: `BaseDivineService` → `BaseLocationHook` → per-provider hook
- **Mock state**: Global `FakeLoc` object — shared across all hooks
- **Android version branching**: Heavy use of `Build.VERSION.SDK_INT >=` checks
- **NMEA**: Uses `nmea/` library module (never raw string parsing)
- **Device-specific hooks**: Separate files per OEM (miui/, oplus/)
- **C++ native**: `src/main/cpp/` — CMake build, Dobby library
- Compile SDK 35, minSdk 24, ndkVersion 26.1.10909125

## ANTI-PATTERNS

- `kotlin.runCatching {}` with empty `onFailure` — don't silence errors silently
- `SystemSensorManagerHook` has empty `onSensorChanged` hook + commented-out step sensor injection (步频未实现)

## COMMANDS

```bash
# Build xposed module as dependency (built via :app assemble)
./gradlew :xposed:assembleRelease
./gradlew :xposed:test
```
