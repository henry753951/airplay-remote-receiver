# Architecture

Android TV AirPlay Hub combines two independently enabled network receivers with
a user-started HomePod audio sender. The receivers share the TV name,
foreground-service lifetime and power policy. The sender has its own foreground
service and capture consent. The Android source namespace is
`io.github.henry753951.airplayremote`.

## Boundaries

| Package | Responsibility |
| --- | --- |
| `ui`, `ui.viewmodel` | Compose screens, focus and observable presentation state |
| `runtime` | Android service lifetime, power ownership and AirPlay receiver identity |
| `airplay.bridge`, `airplay.discovery` | Native AirPlay adapter and service discovery |
| `playback.audio`, `playback.renderer`, `playback.download` | Transport controls, media decoding, rendering and downloads |
| `remote` | Remote receiver lifecycle and process-local observable state |
| `remote.protocol` | Kotlin/JNI adapter to the Rust remote protocol library |
| `remote.identity` | Device-protected pairing identity and input preferences |
| `remote.input` | Input strategies, persistent local ADB and accessibility integration |
| `platform` | Network-interface inspection |
| `audiooutput` | Speaker discovery, capture consent session and sender service |
| `audiooutput.protocol` | Adapted AirBridge sender, ALAC, crypto and clock calibration |

`AirPlayService` is the Android lifecycle coordinator. It delegates CPU residency
to `ReceiverPowerManager` and stable identity to `AirPlayIdentity`; neither belongs
to a screen. `RemoteEngine` reports pairing requests through a callback supplied by
the coordinator, without depending on an Activity. Native callbacks and StateFlow
carry events to the UI; no polling is introduced by these boundaries.

`InputRouter` uses ordered, lazy input strategies. Automatic mode tries hardware,
then local ADB, then accessibility. Explicit modes call only their selected
backend. An accepted command is never replayed through a later strategy. The ADB
adapter retains the persistent input channel rather than spawning a process per key.

These boundaries apply single responsibility and dependency inversion where the
runtime has concrete responsibilities to separate. Native adapters encapsulate
protocol integration; observable state carries receiver events; the input chain
encapsulates backend selection. Additional modules and dependency-injection
interfaces are unnecessary for these small platform adapters.

## Installation contracts

The public application ID remains `io.github.henry753951.airplaytv`. Source package
names and application IDs have different roles: the latter, together with the
signing certificate, determines whether Android accepts an in-place update.

`InstallationContract` isolates persisted identifiers. Settings filenames, pairing
keys, controller records and the device UUID derivation remain unchanged. A legacy
launcher alias and two small component adapters preserve existing shortcuts,
protected settings automation and accessibility grants. Legacy names outside
this compatibility boundary must not be used for new implementation code.

The settings receiver remains protected by `android.permission.DUMP`; native input
and callback classes retain explicit R8 keep rules. Kotlin class moves require
matching C++ and Rust JNI export changes and a fresh native-library build.

## Session lifetime

Disconnect, Back during casting and returning to TV Home end the current cast.
They do not disable AirPlay discovery or the enabled Remote receiver. Full service
shutdown is a separate operation. Boot startup and display wake follow the user's
saved power preferences. `ReceiverPowerManager` owns the single CPU wake lock and
releases it according to the existing standby policy.

## Playback compatibility

The decoder, Oboe audio callback and GPU render paths remain independent of UI
navigation. SDR color policy retains the supported sRGB and SMPTE 170M transfers;
HDR and unsupported formats continue through their established paths. Refactoring
package boundaries must not silently change codec selection, buffering, frame
scheduling, color conversion or session-disconnect semantics.

## Development

Use the pinned toolchains and build commands in [README](../README.md). Build the
Rust Android adapter before Gradle. Validate release shrinking and lint after
moving JNI classes. Unit checks cover settings validation, color and power policies,
and input fallback without replay. Interactive playback and TV navigation remain
separate device-validation steps.

Third-party protocol implementations and notices are documented in
[THIRD_PARTY_NOTICES](../THIRD_PARTY_NOTICES.md); package ownership does not replace
upstream copyright or license obligations.

References: [Android namespace and application ID](https://developer.android.com/build/configure-app-module),
[Activity aliases](https://developer.android.com/guide/topics/manifest/activity-alias-element).
