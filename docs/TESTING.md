# Testing

WireRifter uses root-level scripts to keep checks repeatable across platforms.

## Scripts

| Platform | Script |
| --- | --- |
| Windows | `tests/run_all.ps1` |
| Linux/macOS | `tests/run_all.sh` |

## What They Check

The scripts perform:

- repository text hygiene scan
- Rust formatting check when `cargo fmt` is available
- Rust `cargo check`
- Rust `cargo test`
- Android Gradle checks when an Android SDK is configured

The Android step is skipped when no SDK is available. This keeps Rust and repository checks useful on machines that do not have Android Studio installed.

## Windows

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\run_all.ps1
```

To skip Android explicitly:

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\run_all.ps1 -SkipAndroid
```

## Linux/macOS

```sh
sh ./tests/run_all.sh
```

To skip Android explicitly:

```sh
SKIP_ANDROID=1 sh ./tests/run_all.sh
```

## Android SDK

For terminal Gradle checks, set a valid SDK path:

```properties
sdk.dir=C\:\\Users\\YOUR_NAME\\AppData\\Local\\Android\\Sdk
```

or set `ANDROID_HOME` / `ANDROID_SDK_ROOT`.
