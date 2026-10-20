<img src="docs/assets/icon.svg" width="88" alt="AirPlay & Remote Receiver icon" />

# AirPlay & Remote Receiver

AirPlay and Apple TV Remote reception in one Android TV app. Mirror your iPhone,
iPad or Mac, then navigate the TV with the built-in iPhone remote. Both receivers
share a name, a restrained TV interface and one background-service lifecycle.

[![Build](https://github.com/henry753951/airplay-remote-receiver/actions/workflows/build.yml/badge.svg)](https://github.com/henry753951/airplay-remote-receiver/actions/workflows/build.yml)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

**[Download](https://github.com/henry753951/airplay-remote-receiver/releases/latest)** ·
**[繁體中文](docs/README.zh-TW.md)** · [Contributing](CONTRIBUTING.md)

## Features

- Screen mirroring and audio reception from Apple devices.
- iPhone Control Center Apple TV Remote reception with authenticated pairing.
- One TV name and one APK; no companion phone app required.
- Mac mirroring or extended desktop, when supported by the sender.
- A restrained TV interface with visible D-pad focus and short transitions.
- English and Traditional Chinese UI, including a full-screen pairing code.
- Foreground background reception and optional startup after boot.
- Optional foreground activation and display wake on connection, subject to firmware.
- Categorized settings for picture, sound, remote control and background operation.
- Source-driven color range by default, manual full/limited overrides and a local grayscale pattern.
- Hardware decoding with configurable resolution, frame rate and audio buffering.

## Requirements

Android 8.0 / API 26 or newer, an `armeabi-v7a` device, and a shared local network
that permits multicast discovery. This release targets 32-bit ARM; other ABIs
are not included. Performance, wake behavior and supported codecs depend on the
device firmware and sender. DRM-protected video is not supported.

## Install and connect

1. Install the signed APK from **Releases** and open **AirPlay & Remote Receiver** on the TV.
2. Allow the requested network/background permissions and review receiver settings.
3. Keep PIN authentication enabled. On your iPhone or iPad, choose **Control Center →
   Screen Mirroring → AirPlay & Remote Receiver**, then enter the code shown on the TV.
4. On Mac, use **Control Center → Screen Mirroring**. To extend the desktop, open
   **System Settings → Displays** and choose the extended-display mode if offered.

The receiver can remain in the background. Standby reception requires the TV to
keep Android and networking alive; fully powered-off or unplugged TVs cannot receive.
Launch-on-connect may wake the display before PIN verification.

### iPhone remote

Open **Control Center → Apple TV Remote**, select the same TV name and enter the
remote pairing code shown on the TV. AirPlay and Remote authenticate separately.
For arrow buttons, enable **Settings → Accessibility → Apple TV Remote → Directional
Buttons** on the iPhone ([Apple guide](https://support.apple.com/en-gb/guide/iphone/iph2ec3a3aa6/ios); availability depends on iOS).

Remote navigation requires a TV input channel. The included local ADB driver
connects only to loopback and needs developer debugging and a one-time TV trust
prompt. The app does not enable debugging or change system security settings.
An optional accessibility service is available through Android settings; capabilities
vary by firmware and it is not equivalent to privileged input injection.

### Background and power

**Keep available in standby** holds one shared partial CPU wake lock for both
receivers. **Eco** releases it when no client is connected; firmware may suspend
network discovery or delay wake. Neither mode can receive with mains power removed.
Video decoder and GL resources are released after mirroring ends. Actual standby
power savings require measurement on the target TV.

### Picture calibration

Color range defaults to **Auto**, leaving it to stream metadata and the decoder.
The receiver previously coupled SDR compatibility to a limited-range override.
Full/limited overrides are troubleshooting controls, not quality enhancements.
Use the local grayscale page as a TV baseline and cast the bundled
[comparison page](docs/grayscale.html) from a browser. AirPlay mirroring is compressed; this app cannot restore detail already
lost by the sender. Decoder metadata is logged when the output format changes.
See [MediaFormat color keys](https://developer.android.com/reference/android/media/MediaFormat).

### Upgrading from separate apps

The combined APK updates AirPlay & Remote Receiver when signed with the same key. Standalone
Remote TV has a separate Android sandbox: it cannot automatically transfer keys,
paired controllers or debugging trust. Pair and authorize the combined app, verify
it, then stop/uninstall the standalone Remote TV receiver to avoid duplicate entries.
Existing AirPlay settings are retained; the name is the single source for both receivers.

## Build

The project uses the checked-in Gradle wrapper and dependency catalog.

| Tool | Version |
| --- | --- |
| JDK | 21 |
| Rust | 1.98.0; target `armv7-linux-androideabi` |
| Gradle / Android Gradle Plugin | 9.8.1 / 9.4.1 |
| Android SDK platform | 37.0 |
| Android NDK | 27.0.12077973 |
| CMake | 3.22.1 |

Install the listed SDK components, set `ANDROID_HOME`, then:

```sh
git clone --recurse-submodules https://github.com/henry753951/airplay-remote-receiver.git
cd airplay-remote-receiver
# Optional toolchain provisioning with mise: mise trust && mise install
rustup target add armv7-linux-androideabi
scripts/build-remote.sh
(cd remote && cargo test --locked -p atv-core)
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintRelease --max-workers 4
```

The unsigned APK is in `app/build/outputs/apk/release/`. Sign it with your own
release key before installation, or use `assembleDebug` for development.
Complete release source archives include the pinned native dependencies and can
be built with the same Rust and Gradle commands. Git is required to apply the included patches.
See [release instructions](docs/RELEASING.md).

## Troubleshooting

If the receiver is missing, check network permissions, multicast filtering and
guest/client isolation. If video stutters, reduce resolution or frame rate before
changing decoder options. If standby reception stops, review the TV's network
standby and background-execution settings. Firmware may enforce deeper sleep.

## License and credits

GPL-3.0; see [LICENSE](LICENSE) and [third-party notices](THIRD_PARTY_NOTICES.md).
Based on [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server),
with UxPlay providing AirPlay/RAOP protocol support. Remote reception includes
[corvofeng/atv-core](https://github.com/corvofeng/atv-core) and protocol work based on pyatv. Independent project; not
affiliated with or endorsed by Apple Inc.
