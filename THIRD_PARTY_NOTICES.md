# Attribution

AirPlay TV includes code from
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
