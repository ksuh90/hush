# Hush

Hush is private voice typing for Android. When a text field has focus and the keyboard is open, Hush shows a small ghost button. Tap the ghost and speak. A gold ring around the ghost grows with your voice. Tap the check mark to put the text at the cursor. Tap X to stop without text. Speech recognition runs on the phone with [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). Hush sends no audio or text to a server.

- Android 13 (API 33) or later, arm64-v8a only.
- English: NVIDIA Parakeet TDT 0.6B v2 int8 (CC-BY-4.0), 662 MB download. It loads in about 2 s while you speak and uses about 780 MB of memory while it is loaded.
- Korean: Whisper Base multilingual int8 (MIT), 161 MB download. It is slower and less accurate than the English model.

Hush downloads the model for the selected language one time. Hush checks the SHA-256 of each file. After the download, Hush uses no network.

## Build

You need the JDK that comes with Android Studio and the Android SDK.

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew testDebugUnitTest lintRelease checkReleaseApk
```

The APK is `app/build/outputs/apk/release/app-release.apk`. The debug key signs it, because Hush is not on Google Play. `checkReleaseApk` runs `scripts/check-apk.sh`. It makes sure that the APK has the v1, v2 and v3 signatures, 16 KB zip alignment, minSdk 33 and arm64-v8a code only. The Samsung installer refuses an APK without the v1 signature ("App not installed").

## Install

1. Copy the APK to the phone and open it, or run `adb install -r app-release.apk`.
2. Open Hush and do the four steps on the screen, one at a time: microphone, language and speech model, accessibility service, battery. Then Hush shows "Hush is ready". Settings is in the ⋮ menu.
3. If Android shows "Restricted setting" for the accessibility service, open Settings > Apps > Hush, tap ⋮, tap "Allow restricted settings", and try again. Do not open app info from Hush: then Android does not show this menu item.

## How it works

- `HushService` is an accessibility service. It shows the bubble when an editable, non-password field has input focus and the keyboard is open.
- `Bubble` is a `TYPE_ACCESSIBILITY_OVERLAY` window. It never takes input focus. `VoiceRing` draws the voice-level ring and the turning arc while Hush transcribes.
- `MicService` is a microphone foreground service. It runs only while Hush records.
- `DictationMachine` has the states idle, recording and transcribing.
- `Inserter` puts the text at the cursor with `commitText` from the accessibility input method. If that is not available, it uses `ACTION_SET_TEXT`. If that fails, it copies the text to the clipboard.
- `Engine` cuts the audio into speech parts with Silero VAD and transcribes them. It loads the model when you tap the ghost and unloads it 60 s after the last use.

- `SetupActivity` shows the first setup step that is not done, then the ready screen. `SettingsActivity` and `DiagnosticsActivity` open from the ⋮ menu.

The UI uses Latin subsets of the Inter and Fredoka fonts (SIL Open Font License 1.1). The license is in `app/src/main/assets/fonts-OFL.txt`.

To add a language, add a `Language` to `Catalog`. To add a model family, add a `Family` value and a branch in `Engine.modelConfig`.
