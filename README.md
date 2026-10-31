# ExteraDive

Fork of [exteraless](https://github.com/exteraless/exteraless) — `org.divelite.messenger`
(Android app, brand «exteradive»). The fork folds in DiveLite/DiveGram
features (auto-reply, quick replies, weather, audio codec/bitrate,
premium emoji, @all & split mentions, phone-number protection, badges &
streak, typing location, and “Disable animations”) into a single **DiveLite**
entry in Settings → More.

API id/hash for the client:
- `20442185` / `9481330893f68903dc4e67c1faf92795` — set in
  `TMessagesProj/build.gradle` and `local.properties`.

## Repository layout

- `TMessagesProj/` — main Android application module.
- `TMessagesProj_Modules/` — media3 / experimental modules.
- `local.properties` — SDK path + personal keys (not committed; template in
  `.example`).
- `APK/` — release artifacts (`ExteraDive-arm64-v8a.apk`,
  `ExteraDive-armeabi-v7a.apk`).

## Requirements

- JDK **21** (temurin/openjdk) — sources target Java 21 + desugaring.
- Android SDK with `compileSdk 37` (minor 2), `buildTools 36.0.0`,
  `ndk 27.2.12479018`, command-line tools installed.
- Gradle (use the bundled wrapper — `gradle/wrapper/gradle-wrapper.jar`).
- `local.properties` at repo root with:
  ```
  sdk.dir=Q:\\\\AndroidSdk
  TELEGRAM_APP_ID=20442185
  TELEGRAM_APP_HASH=9481330893f68903dc4e67c1faf92795
  KEYSTORE_PASS=android
  ALIAS_NAME=androidkey
  ALIAS_PASS=android
  ```

## Build

The signing keystore is `TMessagesProj/release.keystore` (DN `CN=DiveLite`,
alias `androidkey`, password `android`). Builds are configured to soak it
when `KEYSTORE_PASS`/`ALIAS_NAME`/`ALIAS_PASS` are present in
`local.properties`.

```bash
# Windows PowerShell example
$env:NATIVE_TARGET = "arm64-v8a"
java -cp "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain `
  "-Dorg.gradle.jvmargs=-Xmx3500m -XX:MaxMetaspaceSize=768m" :TMessagesProj:assembleRelease

# armv7
$env:NATIVE_TARGET = "armeabi-v7a"  # run again
```

Artifacts end up in `TMessagesProj/build/outputs/apk/release/`. Copy into
`APK/ExteraDive-<abi>.apk` for publishing. `assembleRelease` also builds
Kotlin via the Chaquopy-less build.

## Push notifications

ExteraDive uses its own Firebase project (`exteradive`) with sender id
`930738329393`. For the server to accept push tokens you must upload the
Firebase Service Account JSON [my.telegram.org](https://my.telegram.org)
→ *API development tools* → app `20442185` → *Push notifications*.

On first launch the client obtains an FCM token (`GooglePushListenerServiceProvider`)
and registers it per account. Failed tokens / watchdog status are visible
in Settings → ExteraDive → General → Notifications (Copy status).
Supported service types: **Google FCM** and **microG**.

## OTA updates

OTA fetches from the Telegram channel `@exteradive`:
- Stable channel → posts tagged `#Release`
- Beta channel → posts tagged `#Beta` (or `#Prerelease`)

Each post **must contain the APK files named**
`ExteraDive-arm64-v8a.apk` / `ExteraDive-armeabi-v7a.apk`. The client
downloads from the channel's single-view CDN link and verifies abi matching.

In the app: Settings → ExteraDive → About (tap the logo to manage the
channel and force a check). Auto-check runs every 6 hours if channel
selected (Settings → ExteraDive → General → *updates* flag).

## Notes for contributors

- Keep the side-menu and chat list design untouched by design.
- All DiveLite/DiveGram features are confined to a single `DiveLite` entry
  under “Дополнительно” in “Еще”.
- Saved-messages chat (`Избранное`) must not show DiveGram badge/streak.
- Never mention badges in commit messages.
- Commit style: `exteradive: <short description>` in Russian (existing history).
- Hidden UI rows are expressed as integers `-1` (row index sentinel).

## License

Telegram's open-source code is licensed under the GNU GPL v. 2 or later. This
fork inherits those terms — publish your source accordingly.