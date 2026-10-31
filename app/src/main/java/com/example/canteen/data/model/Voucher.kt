package com.example.canteen.data.model

import com.google.firebase.firestore.DocumentId

data class Voucher(
    @DocumentId
    val code: String = "",
    val title: String = "",
    val type: String = "PERCENT", // "PERCENT" hoặc "AMOUNT"
    val value: Long = 0L,
    val minOrder: Long = 0L,
    val maxDiscount: Long = 0L,
    val validUntil: String = "",  // yyyy-MM-dd
    val maxUses: Int = 0,         // 0 = không giới hạn
    val usedCount: Int = 0,
    val active: Boolean = true
)

