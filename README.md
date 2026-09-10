# YouTube Loop Live — APK-ready Android project

This is a native Android project for Android 10+ (minSdk 29).

## Build APK on an Android phone

1. Extract this ZIP.
2. Open the extracted `youtube-loop-live` folder in an Android IDE that supports Gradle projects (for example AndroidIDE).
3. Let Gradle sync/download the required Android Gradle Plugin and dependencies. Internet is required for the first sync.
4. Build the **debug APK** (`app` module).
5. Install the generated APK on your phone.

The debug build uses Android's standard debug signing, so no private keystore or password is required.

## Important

- The app requires a YouTube RTMP/RTMPS server URL and stream key to start a live stream.
- A selected local video is streamed in loop mode by the foreground service.
- The app requests/uses foreground-service and notification permissions needed for background streaming.
- This project has no `google-services.json`; the Google Services plugin is configured to warn rather than fail when it is absent.
- The project needs an Android SDK compatible with compileSdk 36.

## AndroidIDE note
If AndroidIDE previously showed `./gradlew: No such file or directory`, this package now includes a `gradlew` launcher. Run/build the project from the project root, not from inside `app/`. The first build needs internet access to download Gradle 9.3.1 and Android dependencies.


## Android 10 compatibility fix

The streaming service now:
- copies the selected SAF video into app-private cache before background streaming, which avoids Android 10 scoped-storage/provider access failures;
- tries hardware H.264/AAC first;
- if `MediaCodec.start()` fails on an Android 10 vendor codec, retries once with software codecs;
- records the full codec exception in Logcat instead of only showing `start failed` in the UI.

This does not guarantee every Android 10 phone will encode every video resolution/codec; device MediaCodec support still matters.
