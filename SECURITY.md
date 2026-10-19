# Security

Report vulnerabilities through [GitHub private vulnerability reporting](https://github.com/henry753951/airplay-remote-receiver/security/advisories/new).
Include the affected version, attack prerequisites, reproduction steps and impact.
Do not post credentials, pairing material or unredacted device logs in public issues.

Use the receiver on a trusted local network. Do not expose its discovery or control
ports to the internet. Network segmentation and guest/client isolation can prevent
discovery. Android firmware and sender updates can change protocol compatibility.

The receiver supports on-screen PIN authentication. Keep it enabled on shared
networks. Foreground activation can wake the display before PIN verification when
launch-on-connect is enabled; disable that setting if this is undesirable.

Remote control uses SRP pair-setup, signed controller identities and encrypted
pair-verify sessions. Pairing keys are generated on-device and kept in private app
storage; Android backup is disabled. The app does not bridge ADB to the network.
Its persistent input helper accepts only a fixed set of Android key codes.
Do not enable the optional accessibility service unless its TV-control access is desired.
