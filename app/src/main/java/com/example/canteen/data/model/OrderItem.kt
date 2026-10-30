package com.example.canteen.data.model

data class OrderItem(
    val menuItemId: String = "",
    val name: String = "",
    val price: Long = 0L,
    val quantity: Int = 1
) {
    val subtotal: Long
        get() = price * quantity
}

