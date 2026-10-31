package com.example.canteen.data.model

import com.google.firebase.firestore.DocumentId

data class VoucherRedemption(
    @DocumentId
    val id: String = "",         // "${CODE}_${uid}"
    val code: String = "",
    val userId: String = "",
    val orderId: String = "",
    val date: String = ""        // yyyy-MM-dd
)

