# WireRifter Documentation

This directory documents the major parts of WireRifter and how they fit together.

## Documents

- [Architecture](ARCHITECTURE.md) - end-to-end pipeline and ownership boundaries.
- [Android App](ANDROID_APP.md) - Compose UI, ViewModel state, persistence, and capture service.
- [Rust Engine](RUST_ENGINE.md) - native analyzer modules, parser flow, JNI contract, and robustness rules.
- [Testing](TESTING.md) - repeatable checks and test automation scripts.

## Quick Map

```text
app/src/main/java/com/example
  MainActivity.kt                  Compose shell and bottom tabs
  services/WireRifterVpnService.kt Android capture service
  data/                            Room entities, DAOs, repository, fingerprinting
  ui/                              Screens, theme, ViewModel
  utils/PacketAnalyzerJni.kt       Kotlin bridge to Rust plus fallback analyzer

app/src/main/rust
  src/lib.rs                       JNI entry points and report assembly
  src/analyzer/                    Native protocol, payload, DNS, entropy, and scan analysis
  src/sniffer/                     Fault isolation and worker pool
  src/db/                          Native SQLite persistence

tests/
  run_all.ps1                      Windows automation
  run_all.sh                       Linux/macOS automation
```
