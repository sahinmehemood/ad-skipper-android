# Validation matrix

## Automated checks

| Check | Command | What it establishes |
|---|---|---|
| Unit tests | `./gradlew testDebugUnitTest` | Text normalization, exact action labels, overflow matching, feed-tile classification and adaptive polling policy |
| Android lint | `./gradlew lintDebug` | Static Android, resource and API checks |
| Debug package | `./gradlew assembleDebug` | APK compiles and packages |
| Combined CI | `./gradlew testDebugUnitTest lintDebug assembleDebug` | CI gate for pull requests |

## Manual device matrix

Run on a real device with the current YouTube version. Record device model, Android build, YouTube version, display density, account language and playback mode.

| Scenario | Expected result |
|---|---|
| Normal fullscreen playback, no Sponsored card | No action |
| Sponsored card appears after an ad | Only its own overflow is opened |
| Menu exposes an exact Dismiss action | Dismiss is dispatched without waiting for the menu timeout |
| Dismiss input is dispatched | Verification begins after 250 ms and checks every 125 ms until removal or the 1.5 s deadline |
| Popup omits a My Ad Center heading | Exact Dismiss action is still recognised |
| Sponsored tile in home/search feed with full-tile description | Tile is ignored |
| Mini-player inside a scrollable watch page | Not rejected solely because an ancestor is scrollable |
| Menu has no Dismiss action | No unrelated option is clicked |
| Similar text such as Learn more / Shop now | Never clicked |
| User changes app during a pending menu | No action outside YouTube |
| Menu/window disappears during scanning | Service survives detached-node exceptions |
| Accessibility service is disabled | Poll callbacks are removed and pending state is cleared |
| Picture-in-picture | Verify separately; behaviour is not assumed from fullscreen |
| Network loss / offline playback | No app network dependency is expected |

## Evidence to record

For each test, record:
- Whether the overflow was found.
- Time from overflow activation to menu detection.
- Whether an exact Dismiss label was found.
- Whether ACTION_CLICK or the bounded gesture was used.
- Whether the gesture completed or was cancelled.
- Whether the overlay disappeared in the verification window.
- Whether retries or stand-down occurred.

## What CI cannot prove

CI cannot reproduce YouTube's live accessibility tree, account- or experiment-specific menu variants, OEM window layering, touch delivery, mini-player layout or the outcome of YouTube's own dismissal action. A passing build is not a substitute for this device matrix.
