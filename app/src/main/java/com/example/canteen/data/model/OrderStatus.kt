package com.example.canteen.data.model

enum class OrderStatus {
    RECEIVED,
    PREPARING,
    READY,
    COMPLETED,
    CANCELLED;

    companion object {
        fun fromString(status: String?): OrderStatus {
            return entries.firstOrNull { it.name.equals(status, ignoreCase = true) } ?: RECEIVED
        }
    }
}

