# VirtuMic Core

**The high-performance virtual microphone engine for Android.**

VirtuMic Core is a library that enables Android applications to
create virtual audio policies. It bridges the native gap in inter-app audio
sharing by implementing a loopback-to-mic pattern: intercepting PCM streams
from specific applications or manual sources and injecting them into the system
microphone.

---

> [!WARNING]
> This library interacts with low-level Android audio hardware. Improper
> usage can lead to system-wide audio silence. Always ensure you close handles
> when your service is destroyed.

---

## 📋 Requirements
- **Privileged Permissions**: `MODIFY_AUDIO_ROUTING` and `CAPTURE_AUDIO_OUTPUT`.

## Usage

### Initialize the Engine
The engine is the entry point for all virtual mic operations. It is
privilege-agnostic and only requires a standard `Context`.

```kotlin
val engine = VMicEngine(context)
```

### Deploy a Virtual Mic
Deploying a mic creates the "Sink" device that the Android system recognizes as
a microphone.

```kotlin
val micHandle = engine.deployMic(
    AudioMission(
        format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(48000)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build(),
        presets = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT
        )
    )
)
```

### Patch an App
Create an `AudioPatch` to intercept audio from a specific application UID.
Multiple patches can be connected to the same handle for mixing.

```kotlin
// Create a patch. Use start = true to begin streaming immediately.
val patch = micHandle.sourceApp(
    uid = appUid,
    presets = intArrayOf(MediaRecorder.AudioSource.MIC),
    start = true
)

// Start or resume the audio flow manually
patch.start()

// Halt the audio flow
// keepResources = true (default): Keeps native handles for ~5ms resume.
// keepResources = false: Releases native handles for power saving.
patch.pause(keepResources = false)
```

### Manual Injection
Inject your own PCM data via a standard `AudioTrack`. The handle provides a
track pre-configured for the virtual microphone's injection session.

```kotlin
val track = micHandle.createManualTrack()
track?.play()
track?.write(myPcmData, 0, size)
```

### Cleanup
`VMicHandle` and `AudioPatch` implement `AutoCloseable`. Closing a handle
automatically closes all its active patches and releases system audio policies.

```kotlin
patch.close()
micHandle.close()
```

---

## Architecture & Audio Pathways

### App Capture Loopback (`sourceApp`)
Captures raw 16-bit PCM bytes from an `AudioRecord` (`AUDIO_SOURCE_REMOTE_SUBMIX`) and pumps
them directly into an injection `AudioTrack` using a `PcmTransferJob`. Format handling is **automatic via
the OS**, locked to the format defined in `AudioMission`.

### Manual Injection (`createManualTrack`)
Provides a direct `AudioTrack` for pushing custom PCM byte streams. Format
handling is **manual**: the developer must write raw PCM data matching the
virtual microphone's target format.

---

## Platform Quirks
- **Audio Encoding**: Both `AudioRecord` (`REMOTE_SUBMIX`) and virtual
  microphone injection are strictly locked to `ENCODING_PCM_16BIT`
  (`INT_16_BIT`), as Android's submix HAL and policy pipelines do not support
  floating-point or 32-bit capture/injection.
- **Channel Masks**: Capture uses input masks (`CHANNEL_IN_*`), while
  injection uses output masks (`CHANNEL_OUT_*`). The correct masks are derived
  automatically.

---

## 📄 License

VirtuMic Core is licensed under the [MIT License](../LICENSE).
