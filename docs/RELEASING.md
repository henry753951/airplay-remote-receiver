# Releasing Android TV AirPlay Hub

1. Update the Android version name and version code. Run Rust protocol tests, Android unit tests and all CI checks.
   Build `scripts/build-remote.sh` before Gradle; include `remote/` and its MIT license
   in complete source archives.
2. Build the release APK using the README instructions. The repository contains
   no release keystore; retain the same signing identity for future updates.
3. Sign with Android SDK `apksigner`. Read passwords from protected files or
   environment variables, never command-line literals or committed configuration.
   Verify the signature with `apksigner verify --verbose`.
4. Inspect the APK, commit and source archive for credentials, private paths and
   runtime data. Compute SHA-256 checksums for release assets.
5. Tag the exact source commit, create a GitHub release, and attach the signed APK,
   complete source archive and `SHA256SUMS`. The source archive must include pinned
   submodules and their license notices. Exclude Git metadata and build outputs.

CI artifacts are unsigned build outputs for development. The signed APK attached
to a GitHub release is the installable distribution. Self-built APKs use your own
signing identity and may not update an APK signed by another key.

Use **Android TV AirPlay Hub** in release titles and
`android-tv-airplay-hub-<version>-<artifact>` for new download filenames. Keep
historical release asset filenames unchanged. Release notes
should describe the version's features, requirements and known limitations.
Preserve historical tags, signing identities and the features actually present
in each version.

Stable releases use an unsuffixed version such as `0.2.0` in the APK, tag and
download filenames. Update both READMEs to link to that version. Publish the
GitHub release with prerelease disabled and mark it as Latest; do not rename an
older APK or move a historical tag to represent a new version.
