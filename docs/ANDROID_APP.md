# Android App

The Android side is responsible for capture orchestration, persistence, user interaction, and graceful fallbacks.

## Key Files

| File | Role |
| --- | --- |
| `MainActivity.kt` | Compose app shell, navigation tabs, theme selection. |
| `WireRifterViewModel.kt` | Runtime state, settings, terminal commands, capture controls. |
| `WireRifterVpnService.kt` | Android capture service and packet processing loop. |
| `PacketAnalyzerJni.kt` | JNI bridge to Rust with Kotlin fallback analysis. |
| `NetworkRepository.kt` | Room persistence, device inventory, PCAP export, discovery helpers. |
| `OsFingerprintEngine.kt` | Passive OS and device fingerprint enrichment. |

## UI Tabs

| Tab | Responsibilities |
| --- | --- |
| Monitor | Capture start/stop, packet feed, query bar, packet inspector. |
| Devices | Device list, topology view, trust state, OS fingerprints. |
| Analysis | IDS-style rules, local audit report, packet forensics. |
| Stats | Protocol mix, top talkers, scan signals, byte counters. |
| Terminal | Safe local helper commands without arbitrary shell execution. |
| Vault | Sessions, PCAP export, settings, license notice, project info. |

## Settings

Vault contains capture and appearance settings:

- power saver capture
- deep packet inspection
- device care mode
- scan sensitivity
- packet retention
- alert cooldown
- system/dark/light appearance mode

## Persistence

Room entities live in `app/src/main/java/com/example/data/database`.

The Android database is the primary app store. The Rust SQLite side log is a native diagnostic and persistence supplement for analyzer-generated records.

## PCAP Export

PCAP export is handled by `NetworkRepository`. It writes saved packet records into standard `.pcap` files with raw packet bytes when available and safe fallback byte handling when packet bodies are partial.
