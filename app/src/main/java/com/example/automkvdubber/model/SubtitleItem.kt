package com.example.automkvdubber.model

/**
 * Represents an individual subtitle line with timing and text.
 */
data class SubtitleItem(
    val index: Int,
    val startTimeStr: String,
    val endTimeStr: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val originalText: String,
    var translatedText: String = ""
) {
    val durationMs: Long
        get() = (endTimeMs - startTimeMs).coerceAtLeast(100L)
}
