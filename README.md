# WireRifter

> Defensive mobile network inspection, packet capture, local analysis, and device fingerprinting for Android.

![Platform](https://img.shields.io/badge/platform-Android-617fd0)
![Core](https://img.shields.io/badge/core-Kotlin%20%2B%20Rust-617fd0)
![Storage](https://img.shields.io/badge/storage-Room%20%2B%20PCAP-617fd0)
![License](https://img.shields.io/badge/license-Proprietary%20Source--Available-617fd0)

WireRifter is an Android security research app built for people who need to understand what is happening on a local network from a mobile device. It combines a Kotlin/Jetpack Compose interface with a Rust-powered analysis layer, local persistence, packet inspection, PCAP export, host inventory, OS fingerprinting, and scan-sense alerting.

It is designed for authorized network visibility, defensive investigation, lab work, mobile-first cybersecurity research, and analyst workflows where the device should explain what it sees instead of hiding the raw details.

## What It Does

- Captures and models packet activity through an Android-safe local capture workflow.
- Stores packet/session/device data locally with Room persistence.
- Lets analysts inspect individual packets, decoded payload previews, headers, flags, entropy, heuristic class, and scan signals.
- Exports saved packet archives as `.pcap` files for external packet-analysis workflows.
- Builds a local device inventory with vendor hints, trust state, care state, and OS fingerprint confidence.
- Detects scan-like behavior such as SYN bursts, invalid TCP flag combinations, null probes, and stealthy service discovery patterns.
- Provides configurable capture settings for power use, packet retention, alert cooldown, deep inspection, and scan sensitivity.
- Includes a local terminal tab for safe network helper commands without executing arbitrary shell commands.
- Uses a clean mobile UI with Monitor, Devices, Analysis, Stats, Terminal, and Vault tabs.

## Design Goals

WireRifter is built around four ideas:

1. **Tell the truth locally.** Packet, device, and session data stays on the phone unless the user exports it.
2. **Show the raw evidence.** Analysts can inspect packets instead of only seeing filtered summaries.
3. **Respect the network body.** Device-care mode reduces noisy repeated actions and treats every node as part of a living local environment.
4. **Prefer explainable signals.** Alerts include why something looks suspicious, not just that it is suspicious.

## Pipeline

```text
Android capture surface
        |
        v
Kotlin capture controller
        |
        +--> live packet feed
        +--> power/care throttling
        +--> scan-sense event stream
        |
        v
Rust analysis core
        |
        +--> byte/frame parsing
        +--> entropy and density checks
        +--> protocol and header summaries
        +--> fault isolation boundaries
        |
        v
Local intelligence layer
        |
        +--> IDS-style rule matching
        +--> OS fingerprint enrichment
        +--> device trust and care state
        +--> session scoring
        |
        v
Room database + PCAP writer
        |
        +--> packet archive
        +--> capture sessions
        +--> device inventory
        +--> exportable .pcap files
        |
        v
Compose analyst UI
```

## App Tabs

| Tab | Purpose |
| --- | --- |
| Monitor | Live capture controls, packet feed, query bar, selected packet inspection, payload and header detail. |
| Devices | Local node inventory, topology/list views, trust controls, OS fingerprints, care state, and confidence scores. |
| Analysis | Rule-based local forensics, audit summaries, custom signatures, and packet risk explanations. |
| Stats | Protocol mix, packet volume, byte volume, top talkers, and scan-sense summaries. |
| Terminal | Safe in-app commands such as `ipconfig`, `ping`, `dns`, `arp`, `scan.net`, `subnet`, and `whois`. |
| Vault | Saved sessions, PCAP export, data reset, capture settings, appearance mode, and reference notes. |

## Rust Side

The Rust module is responsible for robust packet-oriented analysis work where speed and fault boundaries matter. The project includes:

- packet parsing helpers
- sniffer module boundaries
- thread-pool infrastructure
- fault isolation logic
- structured outputs for Kotlin/JNI integration
- local analysis primitives used by the Android layer

Run the Rust check from:

```powershell
cd app/src/main/rust
cargo check
```

## Persistence

WireRifter uses local Room entities for:

- packet logs
- network devices
- IDS rules
- saved capture sessions

Saved packet data can be exported to `.pcap`, which makes the archive portable for desktop forensic review and long-term evidence handling.

## OS Fingerprinting

The fingerprinting layer combines passive traits such as TTL patterns, vendor hints, hostname/service clues, device role, and observed protocol signals. It produces:

- OS family
- likely OS/version label
- confidence score
- stack traits
- care state
- trust score contribution

The goal is not magic certainty. The goal is useful analyst-grade probability with visible confidence.

## Scan Sense

WireRifter watches for scan indicators while capture is active:

- repeated connection probes
- unusual TCP flag combinations
- null-style probes
- stealth service-discovery patterns
- suspicious source concentration
- repeated access to common admin/service ports

When a signal is detected, the app keeps the evidence visible in the packet feed and Statistics tab, and can raise a local notification.

## Appearance

The UI uses `#617fd0` as the primary WireRifter blue. The settings area includes:

- System default mode
- Dark mode
- Light mode

## Building

Prerequisites:

- Android Studio
- Android SDK configured locally
- JDK compatible with the Android Gradle plugin
- Rust toolchain for the Rust module

Open the project in Android Studio, let Gradle sync, and run the `app` configuration on an emulator or physical Android device.

If building from the terminal, make sure `local.properties` points at a real SDK path:

```properties
sdk.dir=C\:\\Users\\YOUR_NAME\\AppData\\Local\\Android\\Sdk
```

Then run:

```powershell
gradle :app:assembleDebug
```

## Responsible Use

WireRifter is intended for networks, devices, and labs where you have permission to inspect traffic. Packet capture and network analysis can expose sensitive data. Use it carefully, store exports securely, and follow the laws and policies that apply to your environment.

## Project Status

WireRifter is an active security research project. Some capture paths depend on Android platform restrictions, device capabilities, permissions, and whether the app is running in a lab, emulator, or real network environment.

## License

WireRifter is proprietary, source-available software. Personal, educational, and internal authorized security use is permitted, but modification, redistribution, commercial use, rebranding, sublicensing, or publication of derivative works requires prior written permission from voltsparx.

Attribution and copyright notices must remain intact in every permitted use.

See [LICENSE](LICENSE) for the full terms.
