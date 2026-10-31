# Changelog

## 1.3.3

- Reduce the post-dismiss verification delay and poll verification independently from the open-menu state.
- Keep the fast menu cadence only while resolving the YouTube options menu; use a lighter verification cadence afterwards.
- Avoid falling back to the slower foreground polling interval during dismissal verification.
- Extract the adaptive polling schedule into a pure policy with unit tests for menu, verification, foreground and idle states.

## 1.3.2

- Shorten pending-menu polling and react to window changes and view clicks.
- Recognise exact Dismiss actions even when YouTube omits a separate My Ad Center heading.
- Avoid false feed classification when a mini-player is nested inside a scrollable watch page.
- Verify actual Sponsored-card disappearance after dispatching the action.
- Add broader matcher tests, Android lint to CI, pinned GitHub Actions and a Gradle distribution checksum.
- Document architecture, security expectations and a manual device-validation matrix.

## 1.3.1

- Add a bounded row-click search, targeted Dismiss-label gesture fallback and menu diagnostics.
- Support known menu-label variants.
- Add a post-action removal check.
