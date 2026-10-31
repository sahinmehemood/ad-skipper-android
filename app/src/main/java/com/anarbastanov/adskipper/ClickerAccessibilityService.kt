package com.anarbastanov.adskipper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Watches only the official YouTube package for its Skip Ad control and the
 * in-player Sponsored overlay. Accessibility events wake the monitor quickly;
 * a bounded polling loop also covers menu animations that emit no follow-up
 * accessibility event.
 */
class ClickerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AdSkipper"
        private const val TARGET_PACKAGE = "com.google.android.youtube"
        private const val SCAN_COOLDOWN_MS = 150L
        private const val SKIP_CLICK_DEBOUNCE_MS = 3_000L
    }

    private val targetViewId = "${TARGET_PACKAGE}:id/skip_ad_button"
    private val handler = Handler(Looper.getMainLooper())

    private val tmpRect = Rect()
    private val tapPath = Path()

    private var lastScanAt = 0L
    private var lastSkipClickAt = 0L
    private var pollScheduled = false
    private var sponsoredOverlayDismisser: SponsoredOverlayDismisser? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        sponsoredOverlayDismisser = SponsoredOverlayDismisser(this)

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            packageNames = arrayOf(TARGET_PACKAGE)
        }

        schedulePoll(0L)
        Log.i(TAG, "Accessibility service connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != TARGET_PACKAGE) return
        // Poll on the main thread and coalesce bursts of accessibility events.
        schedulePoll(0L)
    }

    private fun schedulePoll(delayMs: Long) {
        if (pollScheduled) {
            if (delayMs != 0L) return
            handler.removeCallbacks(pollRunnable)
        }
        pollScheduled = true
        handler.postDelayed(pollRunnable, delayMs)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            pollScheduled = false
            val now = SystemClock.uptimeMillis()
            val dismisser = sponsoredOverlayDismisser
            var activePackage: String? = null

            try {
                // tick() searches YouTube's readable windows, including a menu
                // window that may not be the active window during animation.
                dismisser?.tick(now)

                val root = rootInActiveWindow
                activePackage = root?.packageName?.toString()
                if (root != null && activePackage == TARGET_PACKAGE) {
                    if (now - lastScanAt >= SCAN_COOLDOWN_MS) {
                        lastScanAt = now
                        dismisser?.scan(root, now)
                        scanSkipButton(root, now)
                    }
                }
            } catch (error: RuntimeException) {
                // A YouTube transition can detach a node between any two reads.
                Log.d(TAG, "Ignoring an unstable accessibility tree.", error)
            } finally {
                // While YouTube is foregrounded, monitor menu changes frequently.
                // Otherwise keep a low-rate check so a menu opened moments before
                // a focus transition still gets resolved.
                val nextDelay = PollingPolicy.nextDelay(
                    menuPending = dismisser?.hasPendingMenu() == true,
                    verificationPending = dismisser?.hasPendingVerification() == true,
                    youtubeForeground = activePackage == TARGET_PACKAGE
                )
                schedulePoll(nextDelay)
            }
        }
    }

    private fun scanSkipButton(root: AccessibilityNodeInfo, now: Long) {
        if (now - lastSkipClickAt < SKIP_CLICK_DEBOUNCE_MS) return

        val nodes = root.findAccessibilityNodeInfosByViewId(targetViewId).orEmpty()
        val target = nodes.firstOrNull { it.isVisibleToUser && it.isEnabled } ?: return

        if (clickNodeOrParent(target)) {
            lastSkipClickAt = now
            Log.i(TAG, "Clicked YouTube's Skip Ad control.")
        }
    }

    override fun onInterrupt() {
        // Android may interrupt feedback without unbinding the service.
        // Keep monitoring; teardown belongs in onUnbind/onDestroy.
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        shutDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutDown()
        super.onDestroy()
    }

    private fun shutDown() {
        handler.removeCallbacks(pollRunnable)
        pollScheduled = false
        sponsoredOverlayDismisser?.reset()
        sponsoredOverlayDismisser = null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var hops = 0

        while (current != null && hops < 6) {
            if (current.isVisibleToUser && current.isEnabled && current.isClickable &&
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
            hops++
        }

        node.getBoundsInScreen(tmpRect)
        if (tmpRect.isEmpty || tmpRect.centerX() < 0 || tmpRect.centerY() < 0) return false

        tapPath.rewind()
        tapPath.moveTo(tmpRect.exactCenterX(), tmpRect.exactCenterY())
        val stroke = GestureDescription.StrokeDescription(tapPath, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }
}
