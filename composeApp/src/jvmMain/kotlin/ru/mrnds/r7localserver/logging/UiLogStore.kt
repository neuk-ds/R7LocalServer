package ru.mrnds.r7localserver.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UiLogStore {
    private const val MAX_ITEMS = 500
    private val _lines = MutableStateFlow<List<UiLogEntry>>(emptyList())
    val lines: StateFlow<List<UiLogEntry>> = _lines.asStateFlow()

    fun add(entry: UiLogEntry) {
        val currentLines = _lines.value
        val nextLines = if (currentLines.size >= MAX_ITEMS) {
            currentLines.drop(currentLines.size - MAX_ITEMS + 1) + entry
        } else {
            currentLines + entry
        }
        _lines.value = nextLines
    }

    fun clear() {
        _lines.value = emptyList()
    }
}
data class UiLogEntry(
    val level: UiLogLevel,
    val text: String
)
enum class UiLogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}