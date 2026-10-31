package com.anarbastanov.adskipper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.util.Log
import java.util.ArrayDeque

/**
 * Removes YouTube's small in-player Sponsored card after an ad.
 *
 * This is intentionally a guarded two-step interaction:
 * 1. Find a self-identifying overflow control inside the Sponsored card.
 * 2. After it opens, confirm the ad-options menu is present before finding
 *    and clicking an exact Dismiss entry.
 *
 * The ad tile itself is never used as a click target. Unrecognised layouts are
 * left alone rather than guessed at.
 */
internal class SponsoredOverlayDismisser(
    private val service: ClickerAccessibilityService
) {

    companion object {
        private const val TAG = "AdSkipper"
        private const val TARGET_PACKAGE = "com.google.android.youtube"

        private const val MAX_NODES_VISITED = 400
        private const val MAX_CONTAINER_HOPS = 4
        private const val MAX_FEED_ANCESTOR_HOPS = 6
        private const val MAX_CLICKABLE_ANCESTOR_HOPS = 8

        private const val MENU_WAIT_MS = 2_500L
        private const val RETRY_DELAY_MS = 1_500L
        private const val NO_DISMISS_BACKOFF_MS = 60_000L
        private const val SUCCESS_SETTLE_MS = 15_000L
        private const val DISMISS_VERIFY_DELAY_MS = 250L
        private const val VERIFY_RECHECK_INTERVAL_MS = 125L
        private const val DISMISS_VERIFY_TIMEOUT_MS = 1_500L
        private const val MAX_MENU_ROW_AREA_MULTIPLIER = 60L
        private const val FALLBACK_TAP_DURATION_MS = 80L
        private const val DEBUG_SNAPSHOT_THROTTLE_MS = 3_000L
        private const val DEBUG_SNAPSHOT_MAX_NODES = 80

        private const val MAX_ATTEMPTS_PER_WINDOW = 3
        private const val ATTEMPT_WINDOW_MS = 120_000L
        private const val STAND_DOWN_MS = 600_000L
    }

    /** Zero means no menu opened by this class is awaiting resolution. */
    private var menuOpenedAt = 0L
    private var dismissVerificationAt = 0L
    private var dismissVerificationDeadlineAt = 0L
    private var nextAttemptAt = 0L
    private var standDownUntil = 0L
    private var attemptWindowStartAt = 0L
    private var attemptsInWindow = 0
    private var lastMenuSnapshotAt = 0L

    /**
     * Called on the normal YouTube scan. Scanning all visible Sponsored labels
     * avoids letting an unrelated feed card hide the player overlay later in
     * the same accessibility tree.
     */
    fun scan(root: AccessibilityNodeInfo, now: Long) {
        if (!isYouTubeRoot(root) || menuOpenedAt != 0L ||
            dismissVerificationAt != 0L ||
            now < nextAttemptAt || now < standDownUntil) {
            return
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODES_VISITED) {
            val node = queue.removeFirst()
            visited++

            if (node.isVisibleToUser &&
                SponsoredOverlayRules.isBannerLabel(node.text, node.contentDescription)) {
                tryOpenCardMenu(node, now)
                if (menuOpenedAt != 0L) return
                if (now < nextAttemptAt || now < standDownUntil) return
            }

            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                queue.addLast(child)
            }
        }
    }

    /**
     * Resolves only the menu opened by this instance's scoped Sponsored-card
     * overflow click. Back is used only after the menu positively identifies
     * itself as YouTube's ad-options menu.
     */
    fun tick(now: Long) {
        // Android accepting an accessibility action or gesture only means the
        // input was dispatched. Confirm that the Sponsored card actually left
        // the player before recording a successful dismissal.
        if (menuOpenedAt == 0L) {
            if (dismissVerificationAt == 0L || now < dismissVerificationAt) return

            val roots = youtubeRoots()
            if (roots.isEmpty() && now < dismissVerificationDeadlineAt) {
                dismissVerificationAt = now + VERIFY_RECHECK_INTERVAL_MS
                return
            }

            val overlayStillVisible = roots.any(::containsInPlayerOverlay)
            if (overlayStillVisible && now < dismissVerificationDeadlineAt) {
                // YouTube may animate the row away after the click is delivered.
                // Keep checking briefly rather than treating the first stale
                // accessibility snapshot as a failed dismissal.
                dismissVerificationAt = now + VERIFY_RECHECK_INTERVAL_MS
                return
            }

            dismissVerificationAt = 0L
            dismissVerificationDeadlineAt = 0L

            if (overlayStillVisible || roots.isEmpty()) {
                Log.w(
                    TAG,
                    if (overlayStillVisible) {
                        "Dismiss input was dispatched, but the in-player Sponsored overlay remained visible."
                    } else {
                        "Dismiss input was dispatched, but removal could not be verified because no YouTube window was readable."
                    }
                )
                nextAttemptAt = now + RETRY_DELAY_MS
            } else {
                Log.i(TAG, "Verified that the in-player Sponsored overlay disappeared.")
                attemptWindowStartAt = 0L
                attemptsInWindow = 0
                standDownUntil = 0L
                nextAttemptAt = now + SUCCESS_SETTLE_MS
            }
            return
        }

        val expired = now - menuOpenedAt >= MENU_WAIT_MS
        // Window enumeration can still return YouTube's background or PiP
        // window after the user has switched apps. Never interact with those
        // nodes: row clicks, gestures and GLOBAL_ACTION_BACK must be tied to
        // YouTube being the active window at the moment of the action.
        val activeRoot = activeYouTubeRoot()
        if (activeRoot == null) {
            if (expired) {
                Log.d(TAG, "YouTube is no longer the active window; abandoning the pending menu without interacting.")
                menuOpenedAt = 0L
                nextAttemptAt = now + RETRY_DELAY_MS
            }
            return
        }

        val roots = youtubeRoots()

        // This state is entered only after clicking a self-identifying overflow
        // control inside the Sponsored card. An exact Dismiss action in an
        // active YouTube-owned window is sufficient context; requiring a
        // separate "My Ad Center" heading caused false negatives on popup
        // layouts that omit the heading or expose it in another window.
        val dismissMatch = roots.firstNotNullOfOrNull { root ->
            findLabel(root, SponsoredOverlayRules::isDismissLabel)?.let { root to it }
        }

        if (dismissMatch != null) {
            val (root, dismissNode) = dismissMatch
            if (clickMenuEntry(dismissNode, root)) {
                Log.i(TAG, "Dismiss input dispatched; verifying overlay removal.")
                menuOpenedAt = 0L
                dismissVerificationAt = now + DISMISS_VERIFY_DELAY_MS
                dismissVerificationDeadlineAt = now + DISMISS_VERIFY_TIMEOUT_MS
                return
            }

            if (expired) {
                logMenuSnapshot(now, "An exact Dismiss entry was found, but all bounded click methods failed")
                Log.w(TAG, "The Sponsored-card menu exposed Dismiss, but its row refused the click.")
                closeIdentifiedMenu()
                menuOpenedAt = 0L
                nextAttemptAt = now + RETRY_DELAY_MS
            }
            return
        }

        if (expired) {
            val identifiedAdMenu = roots.any(::containsAdMenuMarker)
            logMenuSnapshot(
                now,
                if (identifiedAdMenu) {
                    "The identified ad menu contained no supported Dismiss action"
                } else {
                    "The menu opened from the Sponsored-card control, but exposed no recognised Dismiss action"
                }
            )

            if (identifiedAdMenu) {
                Log.d(TAG, "The identified ad menu has no Dismiss entry; closing it and backing off.")
                closeIdentifiedMenu()
            } else {
                // Never send Back into a player when the opened window cannot
                // be identified. Leave it untouched and avoid repeated taps.
                Log.w(TAG, "Could not identify a Dismiss action in the opened menu; leaving it untouched.")
            }

            menuOpenedAt = 0L
            nextAttemptAt = now + NO_DISMISS_BACKOFF_MS
        }
    }

    fun reset() {
        // Do not send Back while the service is shutting down.
        menuOpenedAt = 0L
        dismissVerificationAt = 0L
        dismissVerificationDeadlineAt = 0L
    }

    /** A menu requires the fastest poll cadence while its options are resolving. */
    fun hasPendingMenu(): Boolean = menuOpenedAt != 0L

    /** Verification has its own cadence; it does not need the menu's faster poll. */
    fun hasPendingVerification(): Boolean = dismissVerificationAt != 0L

    private fun tryOpenCardMenu(banner: AccessibilityNodeInfo, now: Long) {
        if (looksLikeFeedRow(banner)) return

        val overflow = findOverflowNear(banner) ?: return
        if (!allowAttempt(now)) return

        if (clickInsideCard(overflow)) {
            menuOpenedAt = now
            Log.i(TAG, "Opened the in-player Sponsored card's options menu.")
        } else {
            nextAttemptAt = now + RETRY_DELAY_MS
            Log.d(TAG, "The recognised Sponsored-card options control did not accept a click.")
        }
    }

    /**
     * Feed ads also contain a Sponsored label and overflow menu. A scrollable
     * ancestor alone is not enough to classify a feed row: a mini-player can be
     * nested inside a scrollable watch page. Require the positive full-tile
     * "Sponsored ... play video" description instead.
     */
    private fun looksLikeFeedRow(banner: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = banner
        repeat(MAX_FEED_ANCESTOR_HOPS) { hop ->
            val node = current ?: return false
            if (SponsoredOverlayRules.describesFeedTile(node.contentDescription)) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Ignoring Sponsored label as a feed tile: positive full-tile description at hop " +
                        hop + ", class=" + node.className + ", id=" + node.viewIdResourceName + ".")
                }
                return true
            }
            current = node.parent
        }
        return false
    }

    /**
     * Widen the search one ancestor at a time, stopping at the first container
     * that holds a named overflow control. This keeps the candidate scoped to
     * the card and away from YouTube's unrelated player controls.
     */
    private fun findOverflowNear(banner: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var container = banner.parent
        repeat(MAX_CONTAINER_HOPS) {
            val currentContainer = container ?: return null
            val candidate = findOverflowIn(currentContainer)
            if (candidate != null) return candidate
            container = currentContainer.parent
        }
        return null
    }

    private fun findOverflowIn(container: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(container)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODES_VISITED) {
            val node = queue.removeFirst()
            visited++

            if (node.isVisibleToUser && node.isEnabled &&
                SponsoredOverlayRules.isOverflowNode(
                    node.text,
                    node.contentDescription,
                    node.viewIdResourceName
                )) {
                val clickable = clickableAncestor(node, container)
                if (clickable != null) return clickable
            }

            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                queue.addLast(child)
            }
        }
        return null
    }

    /**
     * Never accepts the card container itself as the overflow click target:
     * that container may be the advertiser's clickable tile.
     */
    private fun clickableAncestor(
        start: AccessibilityNodeInfo,
        boundary: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = start
        repeat(MAX_CLICKABLE_ANCESTOR_HOPS + 1) {
            val node = current ?: return null
            if (node.isVisibleToUser && node.isEnabled && node.isClickable &&
                node != boundary) {
                return node
            }
            if (node == boundary) return null
            current = node.parent
        }
        return null
    }

    private fun clickInsideCard(node: AccessibilityNodeInfo): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

    private fun containsAdMenuMarker(root: AccessibilityNodeInfo): Boolean =
        findLabel(root, SponsoredOverlayRules::isAdMenuMarker) != null

    /**
     * Finds the card only when it is an in-player overlay, not a Sponsored row
     * in YouTube's feed or recommendation list.
     */
    private fun containsInPlayerOverlay(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODES_VISITED) {
            val node = queue.removeFirst()
            visited++

            if (node.isVisibleToUser &&
                SponsoredOverlayRules.isBannerLabel(node.text, node.contentDescription) &&
                !looksLikeFeedRow(node)) {
                return true
            }

            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                queue.addLast(child)
            }
        }
        return false
    }

    private fun findLabel(
        root: AccessibilityNodeInfo,
        predicate: (CharSequence?, CharSequence?) -> Boolean
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODES_VISITED) {
            val node = queue.removeFirst()
            visited++

            if (node.isVisibleToUser && predicate(node.text, node.contentDescription)) {
                return node
            }

            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                queue.addLast(child)
            }
        }
        return null
    }

    /**
     * Attempts the exact matched Dismiss row in this order:
     * 1. A plausible clickable ancestor, up to eight levels.
     * 2. The matched label's own accessibility click action.
     * 3. A short tap at the matched label's exact on-screen bounds.
     *
     * The coordinate fallback is reached only after the ad-menu marker and an
     * exact dismissal label have both been found in the same YouTube window.
     */
    private fun clickMenuEntry(
        label: AccessibilityNodeInfo,
        boundary: AccessibilityNodeInfo
    ): Boolean {
        if (!label.isVisibleToUser) return false

        var current: AccessibilityNodeInfo? = label
        var hops = 0

        while (current != null && hops <= MAX_CLICKABLE_ANCESTOR_HOPS) {
            val node = current
            // Never promote a menu row into a click on the whole YouTube window.
            if (node == boundary) break

            if (node.isVisibleToUser && node.isEnabled && node.isClickable &&
                isPlausibleDismissRow(node, label) &&
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "Dismiss entry accepted ACTION_CLICK on a bounded row (ancestor hop " + hops + ").")
                return true
            }

            current = node.parent
            hops++
        }

        if (label.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(TAG, "Dismiss entry accepted ACTION_CLICK on its text label.")
            return true
        }

        return dispatchFallbackTap(label)
    }

    /**
     * Reject clickable ancestors that are much larger than the matched label.
     * This prevents a deep ancestor search from accidentally clicking a whole
     * sheet/container instead of the specific Dismiss row.
     */
    private fun isPlausibleDismissRow(
        candidate: AccessibilityNodeInfo,
        label: AccessibilityNodeInfo
    ): Boolean {
        if (candidate == label) return true

        val rowBounds = Rect()
        val labelBounds = Rect()
        candidate.getBoundsInScreen(rowBounds)
        label.getBoundsInScreen(labelBounds)

        if (rowBounds.isEmpty || labelBounds.isEmpty ||
            !rowBounds.contains(labelBounds.centerX(), labelBounds.centerY())) {
            return false
        }

        val rowArea = rowBounds.width().toLong() * rowBounds.height().toLong()
        val labelArea = labelBounds.width().toLong() * labelBounds.height().toLong()
        if (rowArea <= 0L || labelArea <= 0L) return false

        return rowArea <= labelArea * MAX_MENU_ROW_AREA_MULTIPLIER
    }

    /**
     * Coordinate fallback is tightly scoped to the already-matched Dismiss
     * label. It never taps a generic menu row, the Sponsored card, or the player.
     */
    private fun dispatchFallbackTap(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser || activeYouTubeRoot() == null) return false

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty || bounds.centerX() < 0 || bounds.centerY() < 0) {
            Log.d(TAG, "Dismiss label has no usable screen bounds for a targeted tap.")
            return false
        }

        val tapPath = Path().apply {
            moveTo(bounds.exactCenterX(), bounds.exactCenterY())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    tapPath,
                    0L,
                    FALLBACK_TAP_DURATION_MS
                )
            )
            .build()

        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Android completed the targeted Dismiss-label gesture.")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Android cancelled the targeted Dismiss-label gesture.")
                }
            },
            null
        )
        if (accepted) {
            Log.d(TAG, "Dispatched a short tap at the exact Dismiss-label bounds.")
        } else {
            Log.d(TAG, "Android refused the targeted Dismiss-label gesture.")
        }
        return accepted
    }

    private fun youtubeRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()

        try {
            val activeRoot = service.rootInActiveWindow
            if (activeRoot != null && isYouTubeRoot(activeRoot)) {
                roots.add(activeRoot)
            }
        } catch (_: RuntimeException) {
            // The active window can disappear during a YouTube transition.
        }

        val windows: List<AccessibilityWindowInfo> = try {
            service.windows ?: emptyList()
        } catch (_: RuntimeException) {
            emptyList()
        }

        for (window in windows) {
            try {
                val root = window.root ?: continue
                if (isYouTubeRoot(root) && roots.none { it == root }) {
                    roots.add(root)
                }
            } catch (_: RuntimeException) {
                // Ignore a window that was removed while the tree was read.
            }
        }

        return roots
    }

    private fun activeYouTubeRoot(): AccessibilityNodeInfo? =
        try {
            service.rootInActiveWindow?.takeIf(::isYouTubeRoot)
        } catch (_: RuntimeException) {
            null
        }

    private fun isYouTubeRoot(root: AccessibilityNodeInfo): Boolean =
        root.packageName?.toString() == TARGET_PACKAGE

    private fun allowAttempt(now: Long): Boolean {
        if (attemptWindowStartAt == 0L ||
            now - attemptWindowStartAt > ATTEMPT_WINDOW_MS) {
            attemptWindowStartAt = now
            attemptsInWindow = 0
        }

        attemptsInWindow++
        if (attemptsInWindow <= MAX_ATTEMPTS_PER_WINDOW) return true

        standDownUntil = now + STAND_DOWN_MS
        Log.w(TAG, "Sponsored-card dismissal stood down temporarily after repeated unsuccessful attempts.")
        return false
    }

    /**
     * Screen text is logged only when the user explicitly enables DEBUG logs.
     * It exposes the exact menu labels/clickability for the installed YouTube
     * build, without collecting screen content during normal operation.
     */
    private fun logMenuSnapshot(now: Long, reason: String) {
        if (!Log.isLoggable(TAG, Log.DEBUG) ||
            now - lastMenuSnapshotAt < DEBUG_SNAPSHOT_THROTTLE_MS) {
            return
        }
        lastMenuSnapshotAt = now

        val detail = StringBuilder()
        var shown = 0

        for (root in youtubeRoots()) {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var visited = 0

            while (queue.isNotEmpty() &&
                visited < MAX_NODES_VISITED &&
                shown < DEBUG_SNAPSHOT_MAX_NODES) {
                val node = queue.removeFirst()
                visited++
                val text = node.text?.toString().orEmpty()
                val description = node.contentDescription?.toString().orEmpty()

                if (node.isVisibleToUser &&
                    (text.isNotBlank() || description.isNotBlank() || node.isClickable)) {
                    shown++
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    detail.append("\n  clickable=").append(node.isClickable)
                        .append(" enabled=").append(node.isEnabled)
                        .append(" class=").append(node.className)
                        .append(" id=").append(node.viewIdResourceName)
                        .append(" bounds=").append(bounds)
                        .append(" text=").append(text)
                        .append(" desc=").append(description)
                }

                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    queue.addLast(child)
                }
            }
        }

        Log.d(TAG, reason + ". Visible YouTube menu nodes (" + shown + "):" + detail)
    }

    private fun closeIdentifiedMenu() {
        // GLOBAL_ACTION_BACK targets the currently focused app, not an
        // arbitrary AccessibilityWindowInfo. Never send it unless YouTube is
        // still the active window.
        if (activeYouTubeRoot() == null) {
            Log.d(TAG, "Skipped Back because YouTube is no longer the active window.")
            return
        }
        if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
            Log.d(TAG, "YouTube declined Back while the identified ad menu was open.")
        }
    }
}
