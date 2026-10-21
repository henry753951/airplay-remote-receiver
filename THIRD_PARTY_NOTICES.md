# Attribution

## HomePod audio sender

The Kotlin sender, ALAC codec, crypto helpers, PCM buffer, audio clock, adapted
playback capture and their protocol tests include Apache-2.0 code from
[kerrybob46644-commits/AirBridge](https://github.com/kerrybob46644-commits/AirBridge)
at revision `12d4fa781b86aaac6135e9279044d8a0f81fce21`.
Local changes integrate the package, cancellation, capture cleanup and input
validation. The full upstream license is retained in
[`licenses/AirBridge-Apache-2.0.txt`](licenses/AirBridge-Apache-2.0.txt).
AirBridge credits the Apache-2.0 clean-room
[airplay2-sender-cpp](https://github.com/akustikrausch/airplay2-sender-cpp) reference
and pyatv wire conventions. Bouncy Castle `bcprov-jdk18on` 1.79 supplies crypto
primitives under its [MIT-style license](licenses/Bouncy-Castle.txt). The combined
application remains GPL-3.0.

## Receiver origins

Android TV AirPlay Hub includes code from
[jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server)
(commit `a46cca0a6a1274e9e33c0e750f0f1c1eb1d881ea`), licensed under GPL-3.0.
Original source notices are retained; this distribution remains GPL-3.0.

Native dependencies are pinned Git submodules:

| Component | Source | License |
| --- | --- | --- |
| UxPlay | https://github.com/FDH2/UxPlay | GPL-3.0; bundled components retain their notices |
| FFmpeg | https://github.com/FFmpeg/FFmpeg | LGPL-2.1-or-later for the configured ALAC/AAC decoder build; see its LICENSE.md |
| libplist | https://github.com/libimobiledevice/libplist | LGPL-2.1-or-later; bundled components retain their notices |
| openssl-cmake | https://github.com/viaduck/openssl-cmake | MIT |
| OpenSSL | https://github.com/openssl/openssl | Apache-2.0 |

AndroidX, Oboe and other Android dependencies retain their own license terms.
The pinned source revisions and license files are included in the complete source
archive supplied with each release. UxPlay patches are in `app/src/main/cpp/patches`.

Apple, iPhone, iPad, Apple TV and AirPlay are trademarks of Apple Inc. This project
is independent and is not affiliated with or endorsed by Apple Inc.

License texts and dependency notices are also bundled in the APK asset
`third-party-licenses.txt`.

## Remote reception

The `remote/` workspace and Android input bridge include code from
[corvofeng/atv-core](https://github.com/corvofeng/atv-core)
(upstream commit `8a4ada29bb7cad31e0315c3387b277eb9ab8c988`, integration snapshot
[Remote TV](https://github.com/henry753951/remote-tv/commit/4553e57600a063e57d29b3e059e46ee79a451179)).
These components retain their MIT license in `remote/LICENSE`. The combined
application is distributed under GPL-3.0.

Protocol behavior also follows the MIT-licensed
[pyatv](https://github.com/postlund/pyatv), Copyright (c) 2020 Pierre Ståhl.
The Rust protocol workspace, dependency lockfile and license notices are retained. Android and JNI adapters use the product namespace.
The CLI is included for source completeness; Android ARMv7 is the release target.
Dadb is Apache-2.0. Rust dependency licenses are listed in the bundled notices.
