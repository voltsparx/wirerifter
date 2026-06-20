# Architecture

WireRifter is a mobile-first network inspection app with a Kotlin Android shell and a Rust native analysis core.

## System Flow

```text
Android capture service
        |
        v
PacketAnalyzerJni.kt
        |
        +--> Kotlin fallback analyzer
        |
        v
Rust JNI engine
        |
        +--> bounded packet parser
        +--> DNS and payload inspection
        +--> entropy and density scoring
        +--> scan-signal scoring
        +--> fault-isolated persistence jobs
        |
        v
Room database and native SQLite side log
        |
        v
Compose UI tabs
```

## Main Boundaries

### Android UI

The UI is implemented with Jetpack Compose. `MainActivity.kt` owns the app shell, top status header, and bottom tabs:

- Monitor
- Devices
- Analysis
- Stats
- Terminal
- Vault

The UI reads state from `WireRifterViewModel` and sends user actions back through ViewModel methods.

### ViewModel

`WireRifterViewModel` is the app control plane. It owns:

- capture state
- current session ID
- packet feed state
- selected packet inspector state
- settings state
- terminal command state
- audit and export status

### Repository and Persistence

`NetworkRepository` coordinates Room persistence, device discovery, PCAP export, and seed data. Room stores:

- packet logs
- network devices
- IDS rules
- saved sessions

### Rust Native Engine

The Rust crate provides a native analyzer behind `PacketAnalyzerJni.kt`. Its job is to do bounded packet parsing, payload scanning, entropy checks, scan-signal scoring, and optional native persistence without crashing the Android process.

## Failure Strategy

WireRifter treats native analysis as an acceleration and robustness layer, not a single point of failure.

- JNI calls are wrapped in panic isolation.
- Kotlin fallbacks keep capture usable if the native library is unavailable.
- Rust parser functions return structured warnings instead of panicking.
- Background persistence jobs run through a thread pool that catches job panics.
- Android UI state remains local and recoverable.
