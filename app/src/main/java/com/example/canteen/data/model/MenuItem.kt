package com.example.canteen.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude

data class MenuItem(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Long = 0L,
    val imageUrl: String = "",
    val category: String = "",
    val inStock: Boolean = true,
    val date: String = "", // format yyyy-MM-dd
    val dishKey: String = "",
    val tags: List<String> = emptyList(),
    val dailyQuota: Int = 0,
    val soldQty: Int = 0,
    val costPrice: Long = 0L
) {
    @get:Exclude
    val remainingQty: Int?
        get() = if (dailyQuota > 0) (dailyQuota - soldQty).coerceAtLeast(0) else null

    @get:Exclude
    val isPopular: Boolean
        get() = soldQty >= 10
}
