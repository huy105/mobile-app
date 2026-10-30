package com.example.canteen.data.repository

import com.example.canteen.data.model.Order
import com.example.canteen.data.model.OrderItem
import com.example.canteen.data.model.OrderStatus
import kotlinx.coroutines.flow.Flow

interface OrderRepository {
    suspend fun createOrder(
        userId: String,
        userName: String,
        items: List<OrderItem>,
        slotId: String,
        slotLabel: String,
        total: Long,
        date: String
    ): Result<Order>

    fun getTodayOrders(date: String): Flow<List<Order>>
    fun getUserOrders(userId: String): Flow<List<Order>>
    fun getOrderById(orderId: String): Flow<Order?>
    suspend fun updateOrderStatus(orderId: String, newStatus: OrderStatus): Result<Unit>
    suspend fun cancelOrder(orderId: String): Result<Unit>
    suspend fun verifyAndCompletePickupCode(code: String, date: String): Result<Order>
}

