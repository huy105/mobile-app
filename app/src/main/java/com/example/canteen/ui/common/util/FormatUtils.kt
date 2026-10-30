package com.example.canteen.ui.common.util

import java.util.Locale

/**
 * Format Long amount to VND currency string (e.g. 35000 -> "35.000đ", 0 -> "0đ")
 * Following spec: String.format(Locale.US, "%,d", this).replace(',', '.') + "đ"
 */
fun Long.formatVnd(): String {
    return String.format(Locale.US, "%,d", this).replace(',', '.') + "đ"
}

/**
 * Helper to get fallback category emoji
 */
fun getCategoryIcon(category: String): String {
    return when {
        category.contains("Cơm", ignoreCase = true) -> "🍚"
        category.contains("Bún", ignoreCase = true) || category.contains("Mì", ignoreCase = true) || category.contains("Phở", ignoreCase = true) -> "🍜"
        category.contains("Ăn nhanh", ignoreCase = true) || category.contains("Bánh mì", ignoreCase = true) || category.contains("Xôi", ignoreCase = true) -> "🥖"
        category.contains("Đồ uống", ignoreCase = true) || category.contains("Trà", ignoreCase = true) || category.contains("Cà phê", ignoreCase = true) -> "🥤"
        else -> "🍱"
    }
}

