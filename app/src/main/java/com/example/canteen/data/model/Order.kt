package com.example.canteen.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Order(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val items: List<OrderItem> = emptyList(),
    val slotId: String = "",
    val slotLabel: String = "",
    val pickupCode: String = "", // 4 digits
    val status: String = OrderStatus.RECEIVED.name,
    val total: Long = 0L,
    val date: String = "", // format yyyy-MM-dd
    val createdAt: Timestamp = Timestamp.now()
) {
    val orderStatus: OrderStatus
        get() = OrderStatus.fromString(status)
}

