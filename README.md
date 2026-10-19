<img src="docs/assets/icon.svg" width="88" alt="AirPlay TV icon" />

# AirPlay TV

A quiet, TV-first AirPlay receiver for Android. Mirror your iPhone, iPad or Mac
to a large screen with a minimal interface, remote-friendly settings and an
on-screen pairing code.

[![Build](https://github.com/henry753951/airplay-tv/actions/workflows/build.yml/badge.svg)](https://github.com/henry753951/airplay-tv/actions/workflows/build.yml)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

**[Download](https://github.com/henry753951/airplay-tv/releases/latest)** ·
**[繁體中文](docs/README.zh-TW.md)** · [Contributing](CONTRIBUTING.md)

## Features

- Screen mirroring and audio reception from Apple devices.
- Mac mirroring or extended desktop, when supported by the sender.
- A restrained TV interface with visible D-pad focus and short transitions.
- English and Traditional Chinese UI, including a full-screen pairing code.
- Foreground background reception and optional startup after boot.
- Optional foreground activation and display wake on connection, subject to firmware.
- Hardware decoding with configurable resolution, frame rate and audio buffering.

## Requirements

Android 6.0 / API 23 or newer, an `armeabi-v7a` device, and a shared local network
that permits multicast discovery. This release targets 32-bit ARM; other ABIs
are not included. Performance, wake behavior and supported codecs depend on the
device firmware and sender. DRM-protected video is not supported.

## Install and connect

1. Install the signed APK from **Releases** and open **AirPlay TV** on the TV.
2. Allow the requested network/background permissions and review receiver settings.
3. Keep PIN authentication enabled. On your iPhone or iPad, choose **Control Center →
   Screen Mirroring → AirPlay TV**, then enter the code shown on the TV.
4. On Mac, use **Control Center → Screen Mirroring**. To extend the desktop, open
   **System Settings → Displays** and choose the extended-display mode if offered.

The receiver can remain in the background. Standby reception requires the TV to
keep Android and networking alive; fully powered-off or unplugged TVs cannot receive.
Launch-on-connect may wake the display before PIN verification.

## Build

The project uses the checked-in Gradle wrapper and dependency catalog.

| Tool | Version |
| --- | --- |
| JDK | 21 |
| Gradle / Android Gradle Plugin | 9.8.1 / 9.4.1 |
| Android SDK platform | 37.0 |
| Android NDK | 27.0.12077973 |
| CMake | 3.22.1 |

Install the listed SDK components, set `ANDROID_HOME`, then:

```sh
git clone --recurse-submodules https://github.com/henry753951/airplay-tv.git
cd airplay-tv
# Optional toolchain provisioning with mise: mise trust && mise install
./gradlew :app:assembleRelease :app:lintRelease --max-workers 4
```

The unsigned APK is in `app/build/outputs/apk/release/`. Sign it with your own
release key before installation, or use `assembleDebug` for development.
Complete release source archives include the pinned native dependencies and can
be built with the same Gradle command. Git is required to apply the included patches.
See [release instructions](docs/RELEASING.md).

## Troubleshooting

If the receiver is missing, check network permissions, multicast filtering and
guest/client isolation. If video stutters, reduce resolution or frame rate before
changing decoder options. If standby reception stops, review the TV's network
standby and background-execution settings. Firmware may enforce deeper sleep.

## License and credits

GPL-3.0; see [LICENSE](LICENSE) and [third-party notices](THIRD_PARTY_NOTICES.md).
Based on [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server),
with UxPlay providing AirPlay/RAOP protocol support. Independent project; not
affiliated with or endorsed by Apple Inc.
