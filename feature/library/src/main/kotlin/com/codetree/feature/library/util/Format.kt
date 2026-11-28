package com.codetree.feature.library.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 字节数格式化：1024 进制，保留一位小数。 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "${value.toInt()} ${units[unitIndex]}"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }
}

/** 进度百分比（0..100）；总长未知时返回 null。 */
fun formatProgress(bytesRead: Long, contentLength: Long): Int? {
    if (contentLength <= 0L) return null
    val fraction = (bytesRead.toDouble() / contentLength.toDouble()).coerceIn(0.0, 1.0)
    return (fraction * 100).toInt()
}

fun formatTimestamp(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMs))
}

/** 会话/包标题过长时截断显示。 */
fun compact(text: String, maxLength: Int = 28): String =
    if (text.length <= maxLength) text else text.take(maxLength) + "…"
