# VirtuMic Service

**Privileged Media3 `MediaSessionService` for Virtual Microphone Audio Injection.**

VirtuMic Service exposes a Media3 `MediaSessionService` interface, allowing external controllers and UI clients to control virtual microphone streaming, app capture loopbacks, and file playback pipelines over standard `MediaController` APIs.

---

## 📋 Requirements
- **Privileged Permissions**: `MODIFY_AUDIO_ROUTING` and `CAPTURE_AUDIO_OUTPUT`.
- **Application Integration**: Host application must implement `VMicEngineProvider` to supply the `VMicEngine` instance.

---

## Client Interaction Contract

### Connecting to the Service
Clients connect using Media3's `MediaController` with a `SessionToken`. You can optionally supply a stream ID in connection hints to target a specific virtual microphone instance.

```kotlin
val sessionToken = SessionToken(context, ComponentName(context, VMicMediaSessionService::class.java))

val connectionHints = Bundle().apply {
    putString(VMicServiceContract.CONNECTION_HINT_MIC_ID, VMicServiceContract.DEFAULT_MIC_ID)
}

val controllerFuture = MediaController.Builder(context, sessionToken)
    .setConnectionHints(connectionHints)
    .buildAsync()
```

### Streaming Audio Files
To stream a local audio file through the virtual microphone, construct a `MediaItem` carrying the desired technical `AudioFormat` in its metadata extras.

```kotlin
val micFormat = AudioFormat.Builder()
    .setSampleRate(48000)
    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
    .build()

val metadata = MediaMetadata.Builder()
    .setTitle("My Audio File")
    .setExtras(Bundle().apply {
        putParcelable(
            VMicServiceContract.EXTRA_AUDIO_CONFIG,
            AudioConfig(format = micFormat, targetPresets = intArrayOf())
        )
    })
    .build()

val mediaItem = MediaItem.Builder()
    .setUri(audioFileUri)
    .setMediaMetadata(metadata)
    .build()

controller.setMediaItem(mediaItem)
controller.prepare()
controller.play()
```

### App Capture Loopback
To capture and inject audio output from another installed application (e.g. a music player),
use the `appaudio://` URI scheme. The service automatically resolves the package UID and patches the audio stream.

```kotlin
val mediaItem = MediaItem.Builder()
    .setUri(Uri.parse("appaudio://capture?pkg=com.example.music"))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle("Music Player")
            .setExtras(Bundle().apply {
                putParcelable(
                    VMicServiceContract.EXTRA_AUDIO_CONFIG,
                    AudioConfig(
                        format = micFormat,
                        targetPresets = intArrayOf(MediaRecorder.AudioSource.MIC)
                    )
                )
            })
            .build()
    )
    .build()

controller.setMediaItem(mediaItem)
controller.prepare()
controller.play()
```

### Lifecycle & Cleanup
Always stop and release the controller when streaming concludes. The service automatically tears down audio patches and releases session resources upon controller disconnection or `stop()`.

```kotlin
controller.stop()
controller.release()
```

---

## Service Contract Reference - `VMicServiceContract`

| Constant | Value | Purpose |
| :--- | :--- | :--- |
| `CONNECTION_HINT_MIC_ID` | `"MIC_ID"` | Key for targeting a mic ID in connection hints. |
| `DEFAULT_MIC_ID` | `""` | Default mic ID for single-instance setups. |
| `URI_SCHEME_APPAUDIO` | `"appaudio"` | URI scheme for targeted app audio capture (`appaudio://capture?pkg=...`). |
| `EXTRA_AUDIO_CONFIG` | `"com.indidevs.virtumic.extra.AUDIO_CONFIG"` | Bundle key for passing `AudioConfig` parcelable metadata. |

---

## Streaming Mechanisms

VirtuMic Service routes playback and capture through the following pathways depending on the media source:

### File & Stream Playback (ExoPlayer Pipeline)
Used when streaming arbitrary audio files or URI sources.
Leverages ExoPlayer with custom audio processors to adapt (resample) the format
of the streamed media files to the virtual mic's target format.

### App Capture Loopback (`appaudio://`)
Used when capturing live audio output from target application UIDs.
Relies on Android's `AudioPolicy` / `AudioMix` framework configuration
to enforce matching PCM formats at the OS routing level, allowing direct
`AudioRecord` -> `AudioTrack` byte streaming without user-space resampling.

---

## 📄 License

VirtuMic Service is licensed under the [MIT License](../LICENSE).
