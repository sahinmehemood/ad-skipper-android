package com.anarbastanov.adskipper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SponsoredOverlayRulesTest {

    @Test
    fun normalizeCollapsesPunctuationAndWhitespace() {
        assertEquals("my ad center", SponsoredOverlayRules.normalize("  My   Ad Center  › "))
        assertEquals("don t show this ad", SponsoredOverlayRules.normalize("Don’t   show this ad!"))
    }

    @Test
    fun normalizationIsLocaleIndependent() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("dismiss this ad", SponsoredOverlayRules.normalize("DISMISS THIS AD"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun sponsoredMarkerMustBeAnExactLabel() {
        assertTrue(SponsoredOverlayRules.isBannerLabel("Sponsored", null))
        assertTrue(SponsoredOverlayRules.isBannerLabel(null, "SPONSORED"))
        assertTrue(SponsoredOverlayRules.isBannerLabel(" sponsored ", null))
        assertFalse(SponsoredOverlayRules.isBannerLabel("Sponsored content", null))
        assertFalse(SponsoredOverlayRules.isBannerLabel("This video is sponsored", null))
        assertFalse(SponsoredOverlayRules.isBannerLabel("Not sponsored", null))
    }

    @Test
    fun overflowMatcherAcceptsOnlyExplicitMenuControls() {
        val supported = listOf(
            "Action menu for Example video",
            "More options",
            "More actions",
            "Ad options",
            "Ad choices",
            "Overflow menu",
            "Options menu"
        )
        supported.forEach {
            assertTrue("Expected overflow match for: $it",
                SponsoredOverlayRules.isOverflowNode(null, it, null))
        }
        assertTrue(SponsoredOverlayRules.isOverflowNode("Options", null, null))
        assertTrue(SponsoredOverlayRules.isOverflowNode(null, null, "com.google:id/overflow_button"))
        assertFalse(SponsoredOverlayRules.isOverflowNode("Learn more", null, null))
        assertFalse(SponsoredOverlayRules.isOverflowNode("Visit advertiser", null, null))
        assertFalse(SponsoredOverlayRules.isOverflowNode("Shop now", null, null))
    }

    @Test
    fun adMenuMarkersSupportKnownCenterSpellings() {
        assertTrue(SponsoredOverlayRules.isAdMenuMarker("My Ad Center", null))
        assertTrue(SponsoredOverlayRules.isAdMenuMarker(null, "About this ad"))
        assertTrue(SponsoredOverlayRules.isAdMenuMarker("My Ad Centre", null))
        assertTrue(SponsoredOverlayRules.isAdMenuMarker(null, "Ad Centre"))
        assertTrue(SponsoredOverlayRules.isAdMenuMarker(null, "Why this ad"))
        assertFalse(SponsoredOverlayRules.isAdMenuMarker("My Ad Center help", null))
        assertFalse(SponsoredOverlayRules.isAdMenuMarker("Ad settings", null))
    }

    @Test
    fun dismissalMatcherAcceptsKnownExactActionVariants() {
        val supported = listOf(
            "Dismiss",
            "Dismiss ad",
            "Dismiss this ad",
            "Hide this ad",
            "Stop seeing this ad",
            "Don't show this ad",
            "DON’T SHOW THIS AD"
        )
        supported.forEach {
            assertTrue("Expected dismissal match for: $it",
                SponsoredOverlayRules.isDismissLabel(it, null))
        }
        assertTrue(SponsoredOverlayRules.isDismissLabel(null, "Hide this ad"))
    }

    @Test
    fun dismissalMatcherRejectsBroadOrUnrelatedLabels() {
        val rejected = listOf(
            "Dismiss dialog",
            "Dismiss all notifications",
            "Dismiss this notification",
            "Learn more",
            "Visit advertiser",
            "Ad settings",
            "Stop seeing recommendations"
        )
        rejected.forEach {
            assertFalse("Unexpected dismissal match for: $it",
                SponsoredOverlayRules.isDismissLabel(it, null))
        }
    }

    @Test
    fun feedTileDescriptionIsRecognisedSeparately() {
        assertTrue(SponsoredOverlayRules.describesFeedTile(
            "Sponsored - Example brand - 15 seconds - Play video"
        ))
        assertTrue(SponsoredOverlayRules.describesFeedTile(
            "SPONSORED: Example brand • Play video"
        ))
        assertFalse(SponsoredOverlayRules.describesFeedTile("Sponsored"))
        assertFalse(SponsoredOverlayRules.describesFeedTile("Sponsored - Example brand"))
        assertFalse(SponsoredOverlayRules.describesFeedTile("Sponsored recommendation"))
    }
}
