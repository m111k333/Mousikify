package com.mousikify.core.model

data class PlaylistSummary(
    val name: String,
    val type: String, // "DECADE", "GENRE", "VIBE"
    val trackCount: Int,
    val coverUri: String? = null
)
