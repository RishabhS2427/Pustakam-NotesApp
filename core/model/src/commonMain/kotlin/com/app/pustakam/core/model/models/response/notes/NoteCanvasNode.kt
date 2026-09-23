package com.app.pustakam.core.model.models.response.notes

import kotlinx.serialization.Serializable

// 🔄 24-Sep-2026 — one canvas page or widget as it travels inside its note, so both devices lay the note out alike
@Serializable
data class NoteCanvasNode(
    val id: String,
    val kind: String,
    val role: String,
    val name: String = "",
    val contentId: String? = null,
    val parentId: String? = null,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val z: Int = 0,
    val locked: Boolean = false,
    val hidden: Boolean = false,
    val links: List<String> = emptyList(),
    val slotOrder: Double = 0.0,
    val pageOrder: Double = 0.0,
)
