# Hush

Hush is private voice typing for Android. When a text field has focus and the keyboard is open, Hush shows a small ghost button. Tap the ghost and speak. A gold ring shows around the ghost, and the ghost moves with your voice. Tap the check mark to put the text at the cursor. Tap X to stop without text. Speech recognition runs on the phone with [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). Hush sends no audio or text to a server.

**[Download Hush.apk](https://github.com/ksuh90/hush/releases/latest/download/Hush.apk)** and open it on the phone to install or update Hush. On Samsung, the first time, tap Settings in the prompt and turn on "Allow from this source" for the app that opened the file (My Files or the browser).

- Android 13 (API 33) or later, arm64-v8a only.
- English: NVIDIA Parakeet TDT 0.6B v2 int8 (CC-BY-4.0), 662 MB download. It loads in about 2 s while you speak and uses about 780 MB of memory while it is loaded.
- English, second choice: Moonshine Base (MIT), 142 MB download. It is faster and less accurate. Hush keeps one model for each language: when the chosen model is downloaded, Hush deletes the other one.
- Korean: Whisper Base multilingual int8 (MIT), 161 MB download. It is slower and less accurate than the English model.

My words: in Settings, add names and terms (for example PostgreSQL, Kubernetes, Yosemite) with commas between them. With Parakeet v2, Hush spells them your way. In a test on the emulator, 33 of 41 test words were correct with the list and 19 without it. The list put in no word that the speaker did not say. Moonshine and Whisper cannot use the list. Then the section stays, but it is off and says why. The words stay saved when you change the model. The list holds up to 20 words, and each word can have up to 4 words and 40 characters: with more words the engine writes more normal words with a capital letter, and a long entry can make it drop the end of a dictation. A list from Hush 0.5.1 or older that is over these limits keeps its first 20 words that fit, and Settings says one time how many words it removed. Settings shows the words that fit in 2 rows and a "Show all N words" button; the open list ends with "Show less". After an add, the list opens when the new words are in the rows that do not show.

Hush downloads the model for the selected language one time. Hush checks the SHA-256 of each file. After the download, Hush uses no network.

## Build

You need the JDK that comes with Android Studio and the Android SDK.

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew testDebugUnitTest lintRelease checkReleaseApk
```

The APK is `app/build/outputs/apk/release/app-release.apk`. The debug key signs it, because Hush is not on Google Play. With `HUSH_KEYSTORE`, `HUSH_KEYSTORE_PASSWORD`, `HUSH_KEY_ALIAS` and `HUSH_KEY_PASSWORD` set, that key signs it instead (the release workflow does this, see [Releases](#releases)). `checkReleaseApk` runs `scripts/check-apk.sh`. It makes sure that the APK has the v1, v2 and v3 signatures, 16 KB zip alignment, minSdk 33 and arm64-v8a code only. The Samsung installer refuses an APK without the v1 signature ("App not installed").

## Install

1. Open the downloaded `Hush.apk` on the phone. For a local build, copy the APK to the phone and open it, or run `adb install -r app-release.apk`.
2. Open Hush and do the four steps on the screen, one at a time: microphone, language and speech model, accessibility service, battery (you can skip the battery step). Then Hush shows "Hush is ready". Settings is in the ⋮ menu.
3. If Android shows "Restricted setting" for the accessibility service, open Settings > Apps > Hush, tap ⋮, tap "Allow restricted settings", and try again. Do not open app info from Hush: then Android does not show this menu item.

## Releases

To release, push a tag that matches `versionName` in `app/build.gradle.kts`, for example `git tag v0.5.3 && git push origin v0.5.3`. The Release workflow (`.github/workflows/release.yml`) runs the tests, lint and `checkReleaseApk` on GitHub, and attaches `Hush.apk` to the GitHub release of that tag. The Download link above always points to the newest release.

Android installs an APK over an installed Hush only when both have the same signature. The maintainer's builds are signed with the debug key in `~/.android/debug.keystore`, so the workflow needs that key in these repository secrets:

| Secret | Value for the debug key |
| --- | --- |
| `HUSH_KEYSTORE_BASE64` | the keystore file, in base64 |
| `HUSH_KEYSTORE_PASSWORD` | `android` |
| `HUSH_KEY_ALIAS` | `androiddebugkey` |
| `HUSH_KEY_PASSWORD` | `android` |

Set them from the Mac that built the installed APK:

```sh
base64 -i ~/.android/debug.keystore | gh secret set HUSH_KEYSTORE_BASE64 --repo ksuh90/hush
gh secret set HUSH_KEYSTORE_PASSWORD --repo ksuh90/hush --body android
gh secret set HUSH_KEY_ALIAS --repo ksuh90/hush --body androiddebugkey
gh secret set HUSH_KEY_PASSWORD --repo ksuh90/hush --body android
```

To make sure that this is the key of the installed APK, compare the SHA-256 that `"$JAVA_HOME/bin/keytool" -list -v -keystore ~/.android/debug.keystore -storepass android -alias androiddebugkey` prints (in upper case, with colons) with the one from `"$ANDROID_HOME"/build-tools/<version>/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`, with `JAVA_HOME` and `ANDROID_HOME` as in [Build](#build). The workflow prints the digest of each release in its "Check signature" step, and fails when the secrets are set but the APK has another signer.

Without `HUSH_KEYSTORE_BASE64`, the CI machine's debug key signs the APK, and the job log and the release notes say so. That APK cannot update an installed Hush ("App not installed"): uninstall Hush first, which deletes the downloaded models and settings.

## How it works

- `HushService` is an accessibility service. It shows the bubble when an editable, non-password field has input focus and the keyboard is open.
- `Bubble` shows the ghost, X and the check in three `TYPE_ACCESSIBILITY_OVERLAY` windows. They never take input focus. The ghost window does not change size, so the ghost stays in place when the buttons show. `GhostView` draws the ghost and moves it as `GhostMotion` says: the hem flutters, and with louder speech the ghost sways and squashes more. `VoiceRing` draws the ring while Hush records and the turning arc while Hush transcribes. With Remove animations on, the ghost and the arc stay still.
- `SpotPicker` chooses where the ghost starts. It never covers another input field, a button of the app or the cursor line. It covers text only when every free place has text. Of the free places, empty space is best, then a part of a wide clickable row, and near the field (just above it at its right end) is better than far. `HushService` reads the bounds of the field and of the inputs, buttons and text in its window once per focus, again when the field moves or changes size, and again when a view shows or goes in that window. When only the views near the field change, the ghost stays while its place is still clear. A place that the user saved by dragging the ghost always wins.
- `MicService` is a microphone foreground service. It runs only while Hush records.
- `DictationMachine` has the states idle, recording and transcribing.
- `Inserter` puts the text at the cursor with `commitText` from the accessibility input method. If that is not available, it uses `ACTION_SET_TEXT`. If that fails, it copies the text to the clipboard.
- `Engine` cuts the audio into speech parts with Silero VAD and transcribes them. It loads the model when you tap the ghost and unloads it 60 s after the last use. Parakeet decodes with modified beam search and gets the user's words ("My words", `MyWords`) as sherpa-onnx hotwords for each decode, so a change to the list needs no new load. sherpa-onnx needs a SentencePiece vocab to encode the words. The model has only `tokens.txt`, so `ModelStore` makes `bpe.vocab` from it on the phone. A word with a character that the model has no token for does not go to the engine.
- `SetupActivity` shows the first setup step that is not done, then the ready screen. `SettingsActivity` and `DiagnosticsActivity` open from the ⋮ menu.

The UI uses Latin subsets of the Inter and Fredoka fonts (SIL Open Font License 1.1). The license is in `app/src/main/assets/fonts-OFL.txt`.

To add a language, add a `Language` to `Catalog`. To add a model family, add a `Family` value and a branch in `Engine.modelConfig`.

## License

Hush is under the [MIT License](LICENSE). The fonts are under the SIL Open Font License 1.1, and sherpa-onnx is under the Apache License 2.0. The speech models that Hush downloads have their own licenses (listed above).
