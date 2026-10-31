# Architecture

## Runtime boundaries

Ad Skipper is an Android application with one accessibility service. The service is registered for the official YouTube package only; the same package check is repeated before reading or acting on a window.

## Components

- `ClickerAccessibilityService` owns service lifecycle, event coalescing and adaptive polling.
- `SponsoredOverlayDismisser` owns the scoped Sponsored-card interaction and post-action verification.
- `SponsoredOverlayRules` contains pure, deterministic text and description matchers.
- `PollingPolicy` selects a tested cadence for menu, verification, foreground and idle states.
- `MainActivity` only guides the user to Accessibility settings.

## Sponsored-card flow

1. Scan a bounded YouTube accessibility tree for the exact `Sponsored` marker.
2. Reject likely feed tiles only when a full-tile `Sponsored ... play video` description positively identifies them. A scrollable ancestor alone is not sufficient because a mini-player can live inside a scrollable watch page.
3. Search only within a bounded ancestor container for a self-identifying overflow control.
4. Click that control and enter a pending-menu state.
5. Search YouTube-owned windows for an exact supported dismissal action. The action is accepted in this scoped pending state even if the popup omits a separate `My Ad Center` heading.
6. Click a plausible row ancestor or use a short gesture at the exact matched label bounds.
7. Verify that the in-player Sponsored card disappeared before recording success.

## Safety invariants

- Never click the Sponsored card, advertiser identity or a call-to-action.
- Never act on a non-YouTube package.
- Re-check that YouTube is the active window before clicks, gestures or global Back actions; an enumerated background/PiP window is not by itself permission to interact.
- Never click broad text such as `Learn more` or `Shop now`.
- Bound every tree walk and ancestor walk.
- Do not send Back for an unrecognised window.
- Keep retries bounded and stand down when the UI is unsupported.
- Treat an accepted accessibility action or gesture as an input dispatch, not proof of UI success.
- Keep verbose screen-content logs disabled unless DEBUG is explicitly enabled.

## Performance model

Accessibility events wake the service immediately. A low-frequency foreground poll is a safety net. The open-menu state uses a 75 ms poll cadence; post-dismiss verification has a separate 125 ms cadence and a short 250 ms initial delay. This avoids keeping the most aggressive poll running after the menu has already been clicked. Tree traversals are bounded and a scan cooldown prevents repeated work for every event burst.

## Known limitation

YouTube owns the UI being automated. The service cannot guarantee a dismissal when YouTube changes labels, hides content from accessibility, moves the menu to a non-readable window, or ignores the action. Device-level validation is mandatory before describing a build as fully working.
