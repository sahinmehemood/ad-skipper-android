# Ad Skipper

A small Android accessibility helper for the official YouTube app. It automatically activates YouTube's **Skip Ad** control and can remove the small in-player **Sponsored** card after the ad returns to normal playback.

## What it does

- Watches only `com.google.android.youtube`.
- Activates the recognised Skip Ad control.
- Finds the exact Sponsored label and its own options control.
- Opens that menu and searches YouTube-owned windows for an exact dismissal action.
- Verifies that the in-player card disappeared after the dismissal input.
- Ignores likely Sponsored feed tiles using a positive full-tile description; a scrollable ancestor alone does not exclude a mini-player.

This is a post-display UI dismissal, not an ad blocker. It does not prevent an ad or card from loading. YouTube can change its interface, labels and accessibility tree, so compatibility is not guaranteed across every YouTube version, account or playback mode.

## Requirements

- Android 7.1 (API 25) or newer.
- The official YouTube app (`com.google.android.youtube`).
- Ad Skipper enabled in Android Accessibility settings.

## Install

1. Download the debug APK from the successful [GitHub Actions run](https://github.com/sahinmehemood/ad-skipper-android/actions).
2. Extract the artifact ZIP and install the APK.
3. Open Ad Skipper and tap **Open Settings**.
4. Enable Ad Skipper under **Accessibility**.
5. Return to YouTube.

**Updating from an earlier CI debug build:** GitHub runners generate temporary debug-signing keys. Different runs can produce APKs with different signing certificates, so Android may reject an in-place update. Uninstall the earlier Ad Skipper build first, install the new APK, then enable the Accessibility service again. No app account or user content is stored by Ad Skipper. See [Signing](docs/SIGNING.md).

On Android 13 and newer, Android may require **Allow restricted settings** for sideloaded apps: `Settings → Apps → Ad Skipper → ⋮ → Allow restricted settings`. Manufacturer menus vary.

## Build and validate

Requirements: JDK 17 and Android SDK 35.

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions runs the same unit tests, Android lint and debug build for pull requests. The workflow uploads a short-lived debug APK artifact. The accessibility interaction still needs testing against the live YouTube UI; unit tests cannot reproduce YouTube's changing accessibility hierarchy.

## Debugging a failed dismissal

Connect the phone to a computer with Android Debug Bridge (ADB), then enable detailed logs:

```sh
adb shell setprop log.tag.AdSkipper DEBUG
adb logcat -c
```

Reproduce the issue once and capture the logs:

```sh
adb logcat -d -s AdSkipper:D > ad-skipper.log
```

Disable detailed logs afterwards:

```sh
adb shell setprop log.tag.AdSkipper INFO
```

**Privacy:** DEBUG mode can log visible YouTube accessibility text, descriptions, view IDs and screen bounds. Review the file and remove personal information before sharing it. Normal INFO logging does not emit menu snapshots.

## Security and privacy

- The accessibility service is restricted to the official YouTube package in both XML metadata and runtime configuration.
- The app does not declare the `INTERNET` permission.
- No account, password, API key or signing secret is required.
- The implementation uses bounded tree traversals, exact text matching, scoped click targets and retry limits.
- Accessibility access is powerful. Review the source before enabling the service.

Report a security concern privately through GitHub rather than publishing sensitive details in an issue. See [SECURITY.md](SECURITY.md).

## Project structure

```text
app/
  src/main/
    java/com/anarbastanov/adskipper/
      ClickerAccessibilityService.kt
      SponsoredOverlayDismisser.kt
      SponsoredOverlayRules.kt
      PollingPolicy.kt
    res/
      values/
      xml/
  src/test/
    java/com/anarbastanov/adskipper/
      SponsoredOverlayRulesTest.kt
      PollingPolicyTest.kt
docs/
  ARCHITECTURE.md
  VALIDATION.md
```

See [Architecture](docs/ARCHITECTURE.md), [Validation matrix](docs/VALIDATION.md), [Signing](docs/SIGNING.md) and [Contributing](CONTRIBUTING.md).

## Acknowledgements

The conservative two-step overlay-dismissal approach was informed by [Javid-Git/ad-skipper](https://github.com/Javid-Git/ad-skipper), which is MIT-licensed.

## License

MIT. See [LICENSE](LICENSE).
