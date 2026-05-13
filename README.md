# Freemote

![Status](https://img.shields.io/badge/status-WIP-orange)
![Platform](https://img.shields.io/badge/platform-Android-green)
![License](https://img.shields.io/badge/license-MIT-blue)
![Language](https://img.shields.io/badge/language-Java-red)
![Build](https://img.shields.io/badge/build-Gradle-brightgreen)
[![Download](https://img.shields.io/github/v/release/McTRASH692/Freemote?label=Download&style=for-the-badge)](https://github.com/McTRASH692/Freemote/releases/latest)

> **Work in Progress**
> Freemote is actively under development. Some features may be incomplete, unstable, experimental, or currently non-functional.

Freemote is an open-source Android remote-control application focused on wireless display interaction, remote control workflows, and expandable Android-based device integration.

## Supported Brands

| Brand | Status | Connection | Notes |
|-------|--------|------------|-------|
| **Samsung Tizen** | ✅ Working | WebSocket (8001/8002) + REST | Fully functional — keys, touchpad, keyboard, app launch, pairing |
| **Android TV / Google TV** | ⚠️ Partial | TLS socket (port 6466) | Connection implemented; pairing flow incomplete |
| **LG webOS** | 🧪 UNTESTED | WebSocket SSAP (3000/3001) | Full implementation written; no LG TV available to test |
| **Roku** | 🧪 UNTESTED | REST ECP (port 8060) | Full implementation written; no Roku device available to test |
| **Panasonic Viera** | 🧪 UNTESTED | SOAP/HTTP (port 55000) | Implementation written; untested |
| **Philips jointSPACE** | 🧪 UNTESTED | REST JSON (port 1925/1926) | Implementation written; untested |
| **Sony (Android TV)** | ⚠️ Partial | Shares Android TV protocol | Untested |
| **Hisense VIDAA** | 🔴 Skeleton | MQTT (port 36669) | Skeleton only — protocol requires encryption |
| **Sharp Aquos** | 🔴 Skeleton | Raw TCP (port 10002) | Skeleton only — 4-byte command protocol |
| **Haier / Vestel** | 🔴 Skeleton | REST (port 56789/56790) | Skeleton only |
| **Vizio SmartCast** | 🔴 Skeleton | REST HTTPS (port 7345) | Skeleton only — requires pairing |
| **Apple TV** | 🔴 Skeleton | AirPlay / MRP (port 7000) | Skeleton only |
| **Fire TV** | 🔴 Skeleton | ADB (port 5555) | Skeleton only |
| **Xiaomi Mi TV** | 🔴 Skeleton | TCP Keyevent (port 6095) | Skeleton only |
| **TCL (Android TV)** | ⚠️ Partial | Shares Android TV protocol | Untested |

Legend: ✅ Working | ⚠️ Partial | 🧪 UNTESTED (coded, never tested on real hardware) | 🔴 Skeleton (protocol identified, no implementation)

### UNTESTED Notice

Several brand implementations (LG, Roku, Panasonic, Philips) have been fully coded according to their public API specifications but have **never been tested against real hardware**. These implementations follow the documented APIs and should function correctly with minor adjustments. Contributors with access to these TV brands are especially welcome to test and provide feedback.

## Features

> **⚠️ Note:** Android/Google TV support is currently in development. The app has been tested and confirmed working on Samsung Tizen TVs. Android TV functionality is implemented but not yet fully tested.

- Wireless remote control interface
- Display interaction tools
- Mobile-first Android UI
- Lightweight footprint
- Extensible architecture — add new TV brands by implementing `RemoteController`
- Open-source development
- Experimental feature development
- Multi-brand network discovery (NSD, SSDP, port scanning)

## Notes

- Under active development
- Features may change between releases
- Some functionality may be incomplete
- Bugs should be expected during development
- Feedback, testing, and contributions are welcome

## Technology

- Java
- Android SDK
- Gradle
- OkHttp (WebSocket + REST)
- Protocol Buffers (Android TV)
- Android Studio compatible
- Android background services
- Native Android APIs

## Build

Clone:

git clone https://github.com/McTRASH692/Freemote.git

cd Freemote

Build:

./gradlew assembleDebug

Install:

./gradlew installDebug

## Contributing

Contributions, issue reports, feature suggestions, and testing feedback are welcome.

## License

MIT License

See the LICENSE file for full details.

---

## Developer Contact

**Developer:** McTRASH692
**Email:** rgardnerthompson@gmail.com
**GitHub:** https://github.com/McTRASH692
**Project Repository:** https://github.com/McTRASH692/Freemote
