package com.example.canteen.data.model

import com.google.firebase.firestore.DocumentId

data class TimeSlot(
    @DocumentId
    val id: String = "",
    val label: String = "",
    val startMinute: Int = 0,
    val endMinute: Int = 0,
    val maxOrders: Int = 10,
    val currentOrders: Int = 0
) {
    val remainingOrders: Int
        get() = (maxOrders - currentOrders).coerceAtLeast(0)

    val isFull: Boolean
        get() = currentOrders >= maxOrders
}

/** Id of the per-day order counter document: `timeSlotCounters/{slotId}_{yyyy-MM-dd}`. */
fun slotCounterId(slotId: String, date: String): String = "${slotId}_$date"

