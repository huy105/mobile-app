package com.example.canteen.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude

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
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp? = null,
    val preparingAt: Timestamp? = null,
    val readyAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    val cancelledAt: Timestamp? = null,
    val cancelledBy: String = "",     // "STUDENT" | "CANTEEN" | "" (chưa hủy)
    val cancelReason: String = "",    // tên của CancelReason | "" (chưa hủy)
    val subtotal: Long = 0L,          // tạm tính trước giảm giá
    val discountAmount: Long = 0L,    // tổng giảm giá (voucher + điểm)
    val voucherCode: String = "",     // mã voucher đã áp
    val pointsUsed: Int = 0           // điểm rèn luyện đã dùng
) {
    val orderStatus: OrderStatus
        get() = OrderStatus.fromString(status)

    @get:Exclude
    val pointsDiscount: Long
        get() = pointsUsed * 500L

    @get:Exclude
    val voucherDiscount: Long
        get() = discountAmount - pointsDiscount

    @get:Exclude
    val displaySubtotal: Long
        get() = if (subtotal == 0L) total else subtotal
}
