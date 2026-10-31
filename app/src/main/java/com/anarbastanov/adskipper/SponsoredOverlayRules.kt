package com.anarbastanov.adskipper

import java.util.Locale

/**
 * Pure matching rules for the YouTube in-player Sponsored card.
 *
 * Keep these predicates deliberately narrow: a false negative leaves a card
 * visible; a false positive can click an advertiser's call-to-action.
 */
internal object SponsoredOverlayRules {

    private val bannerLabels = setOf("sponsored")

    private val overflowPrefixes = listOf(
        "action menu",
        "more options",
        "more actions",
        "ad options",
        "ad choices",
        "overflow menu",
        "options menu"
    )

    private val overflowExactLabels = setOf("options")

    private val overflowViewIdHints = listOf(
        "overflow",
        "options",
        "kebab",
        "three_dot"
    )

    private val adMenuMarkers = setOf(
        "my ad center",
        "ad center",
        "my ad centre",
        "ad centre",
        "about this ad",
        "why this ad"
    )

    private val dismissLabels = setOf(
        "dismiss",
        "dismiss ad",
        "dismiss this ad",
        "hide this ad",
        "stop seeing this ad",
        "don t show this ad"
    )

    fun normalize(value: CharSequence?): String {
        if (value.isNullOrEmpty()) return ""

        val lowered = value.toString().lowercase(Locale.ROOT)
        val result = StringBuilder(lowered.length)
        var pendingSpace = false

        for (character in lowered) {
            if (character.isLetterOrDigit()) {
                if (pendingSpace && result.isNotEmpty()) result.append(' ')
                pendingSpace = false
                result.append(character)
            } else {
                pendingSpace = true
            }
        }
        return result.toString()
    }

    fun isBannerLabel(text: CharSequence?, description: CharSequence?): Boolean =
        normalize(text) in bannerLabels || normalize(description) in bannerLabels

    fun isOverflowNode(
        text: CharSequence?,
        description: CharSequence?,
        viewId: String?
    ): Boolean {
        val normalizedText = normalize(text)
        val normalizedDescription = normalize(description)

        if (overflowPrefixes.any {
                normalizedText.startsWith(it) || normalizedDescription.startsWith(it)
            }) {
            return true
        }

        if (normalizedText in overflowExactLabels ||
            normalizedDescription in overflowExactLabels) {
            return true
        }

        val normalizedId = viewId?.lowercase(Locale.ROOT) ?: return false
        return overflowViewIdHints.any { hint -> normalizedId.contains(hint) }
    }

    fun isAdMenuMarker(text: CharSequence?, description: CharSequence?): Boolean =
        normalize(text) in adMenuMarkers || normalize(description) in adMenuMarkers

    fun isDismissLabel(text: CharSequence?, description: CharSequence?): Boolean =
        normalize(text) in dismissLabels || normalize(description) in dismissLabels

    fun describesFeedTile(description: CharSequence?): Boolean {
        val normalized = normalize(description)
        return normalized.startsWith("sponsored ") && normalized.contains("play video")
    }
}
