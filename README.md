<div align="center">

# VirtuMic

**The General Purpose Virtual Microphone for Android™.**

<img src="assets/images/logo.png" alt="VirtuMic Logo Image" width="256" height="256"/>

</div>

## Introduction

VirtuMic is a framework and an app for creating virtual microphones on Android. It's based
on the system's built-in audio routing framework to inject audio back into the system
as a live microphone feed.

## Download App

_Coming soon..._

## Requirements

- **Android Version**: Android 13 (API 33) or higher.
- **Permissions**: `MODIFY_AUDIO_ROUTING` and `CAPTURE_AUDIO_OUTPUT`.
  Can be acquired via one of the options below.
  - **ADB Shell Context (rootless)**: Wireless ADB debugging.
  - **Rooted Device**: Systemless overlay (Magisk) module for automated privilege escalation.

## Features

### Audio
- 🎵📁 Stream audio files — ✅
- 📱 Capture and stream apps' audio — ✅
- 📞 Call module — ❌ ⏰ (Planned)

### Operation Modes
- Media3 MediaSessionService (for the app) — ✅
- MediaSessionService for 3rd-party apps — ❌ ⏰ (Planned)
- Media3 Cast — ❌ 🗣️ (TBD)

### Privilege Modes
- ADB (Android 13+) — ✅
- Magic Mount Overlay — ✅
- Shizuku — ❌ ⏰ (Planned)

## How it Works

VirtuMic creates a virtual microphone by redirecting audio within the Android system:

- **System Audio Management**: Android routes audio from sources to sinks (speakers).
  VirtuMic uses a loopback `AudioPolicy` to intercept this flow.
- **Loopback Policy**: VirtuMic registers a policy that captures any audio intended for recording.
  Instead of getting sound from the physical microphone, the system is told to pull it from a
  specific internal injection track that is defined by the policy.
- **Injection**: When you enable streaming, VirtuMic writes the audio data directly into this
  injection track. Because of the policy, the system treats this data as a live microphone feed,
  making it available to any app on your device.

For a technical explanation, refer to the module READMEs: [Core Engine](virtumic-core/README.md), [Audio Service](virtumic-service/README.md).

## Developers

VirtuMic is structured as a multi-module library framework. In addition to the client app, the project provides core modules for library integration:

### [VirtuMic Core (`virtumic-core`)](virtumic-core/README.md)
The high-performance virtual microphone engine for Android. It enables Android
applications to create virtual audio policies, bridging the native gap in
inter-app audio sharing. The library intercepts PCM streams from apps or manual sources
and injects them into the system microphone. It provides `VMicEngine`, a privilege-agnostic
entry point to deploy virtual microphone sinks. For further details, see the [Core README](virtumic-core/README.md).

### [VirtuMic Service (`virtumic-service`)](virtumic-service/README.md)
A privileged Media3 `MediaSessionService` for virtual microphone audio injection.
It exposes a `MediaSessionService` interface, allowing external controllers
and UI clients to control virtual microphone streaming, app capture loopbacks,
and file playback pipelines over standard Media3 `MediaController` APIs.
Host applications supply a `VMicEngine` instance via `VMicEngineProvider`,
and clients connect using Media3 `MediaController` with custom parameters. For further details, see the [Service README](virtumic-service/README.md).

## Audio Quirks

- **16-bit PCM Enforcement**: All capture and injection streams are strictly locked to 16-bit PCM integer (`ENCODING_PCM_16BIT`), required by Android's `REMOTE_SUBMIX` and HAL audio policies.
- **Stereo vs Mono Recording Apps**: When streaming in stereo mode, Android's `AudioFlinger` automatically downmixes stereo virtual microphone input to mono if the recording app requests mono (`AudioFormat.CHANNEL_IN_MONO`). To verify or record true stereo separation, use a recording app that explicitly supports stereo microphone capture (`CHANNEL_IN_STEREO`).

## Release Verification

### APK Signing Certificate
Ensure the app comes from the official developer:
- **SHA-256 Fingerprint**: `E9:8E:67:C5:20:FA:02:E3:E4:83:8C:1C:0C:EE:33:50:BB:0C:84:3B:B0:6B:35:88:D5:6E:7F:60:45:C6:E2:7B`
- Verify locally:
  ```bash
  apksigner verify --print-certs virtumic-full.apk
  ```
  *(Compare the output SHA-256 certificate digest printed by `apksigner` against the pinned fingerprint above).*

### Keyless Cosign Verification (Sigstore OIDC)
Verify that `checksums.txt` was created by CI on the official repository (`main` branch):
```bash
cosign verify-blob \
  --bundle checksums.txt.bundle \
  --certificate-oidc-issuer "https://gitlab.com" \
  --certificate-identity "https://gitlab.com/IndiDevs/VirtuMic//.gitlab-ci.yml@refs/heads/main" \
  checksums.txt
```

### Asset Integrity (Optional)
Ensure the released assets come from the official developer and haven't been corrupted or altered:
- **Pinned GPG Key Fingerprint**: `20BE44F0EA96B41F6FA2B07D29AA514BF4637181`
- Make sure your downloaded APK and `checksums.txt` are placed together in the same folder before running:
  ```bash
  # 1. Fetch and verify GPG signature of checksums.txt
  gpg --keyserver hkps://keyserver.ubuntu.com --recv-keys 20BE44F0EA96B41F6FA2B07D29AA514BF4637181
  gpg --fingerprint 20BE44F0EA96B41F6FA2B07D29AA514BF4637181
  gpg --verify checksums.txt.asc checksums.txt

  # 2. Verify downloaded APK matches the hash in checksums.txt
  sha256sum -c checksums.txt --ignore-missing
  ```
  *(If successful, `sha256sum` prints `virtumic-full.apk: OK` or similar, confirming the asset matches the verified checksums).*

## Contributing & Support

You can help improve the project or get help:

- If you encounter issues or want to request a feature, open an issue
- If you want to contribute code, feel free to open a PR

See [CONTRIBUTING.md](CONTRIBUTING.md) for directions.


## Acknowledgements

- [flyfishxu / Kadb](https://github.com/flyfishxu/Kadb)
- prominent [POC](https://github.com/Genymobile/scrcpy/issues/3880#issuecomment-1595722119) in the
  field by [yume-chan](https://github.com/yume-chan)
- [Taming Bazel Dependency Conflicts: An Android Case Study / Taufik Amaryansyah](https://medium.com/@taufik.amary/taming-bazel-dependency-conflicts-an-android-case-study-44b7926839be)
- [Building a Modern Android Music Player App with Bazel, Jetpack Compose, and ExoPlayer / Taufik Amaryansyah](https://medium.com/@taufik.amary/building-a-modern-android-music-player-app-with-bazel-jetpack-compose-and-exoplayer-4a70039655f2)

## Notice

- This project is an independent development and is not affiliated with,
  endorsed by, or in any way officially connected with Google LLC or any of its subsidiaries.
  Android is a trademark of Google LLC.
