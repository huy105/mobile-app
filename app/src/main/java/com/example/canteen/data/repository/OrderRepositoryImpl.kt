package com.example.canteen.data.repository

import com.example.canteen.data.model.Order
import com.example.canteen.data.model.OrderItem
import com.example.canteen.data.model.OrderStatus
import com.example.canteen.data.model.TimeSlot
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : OrderRepository {

    override suspend fun createOrder(
        userId: String,
        userName: String,
        items: List<OrderItem>,
        slotId: String,
        slotLabel: String,
        total: Long,
        date: String
    ): Result<Order> {
        return try {
            // 1. Gather existing pickup codes for this date to ensure uniqueness
            val existingCodes = firestore.collection("orders")
                .whereEqualTo("date", date)
                .get()
                .await()
                .documents.mapNotNull { it.getString("pickupCode") }.toSet()

            var pickupCode: String
            do {
                pickupCode = String.format(Locale.getDefault(), "%04d", Random.nextInt(10000))
            } while (pickupCode in existingCodes)

            // 2. Execute Firestore transaction for stock and slot capacity validation
            val slotRef = firestore.collection("timeSlots").document(slotId)
            val newOrderRef = firestore.collection("orders").document()

            val createdOrder = firestore.runTransaction { transaction ->
                // Check TimeSlot
                val slotDoc = transaction.get(slotRef)
                if (!slotDoc.exists()) {
                    throw FirebaseFirestoreException("Khung giờ không tồn tại", FirebaseFirestoreException.Code.NOT_FOUND)
                }
                val slot = slotDoc.toObject(TimeSlot::class.java)
                    ?: throw FirebaseFirestoreException("Không thể đọc thông tin khung giờ", FirebaseFirestoreException.Code.ABORTED)

                if (slot.currentOrders >= slot.maxOrders) {
                    throw FirebaseFirestoreException("Khung giờ đã đầy", FirebaseFirestoreException.Code.ABORTED)
                }

                // Check menu items in stock
                for (item in items) {
                    if (item.menuItemId.isNotBlank()) {
                        val itemRef = firestore.collection("menuItems").document(item.menuItemId)
                        val itemDoc = transaction.get(itemRef)
                        if (itemDoc.exists()) {
                            val inStock = itemDoc.getBoolean("inStock") ?: true
                            if (!inStock) {
                                throw FirebaseFirestoreException("Món ${item.name} đã hết hàng", FirebaseFirestoreException.Code.ABORTED)
                            }
                        }
                    }
                }

                // Increment slot currentOrders
                transaction.update(slotRef, "currentOrders", slot.currentOrders + 1)

                // Create Order
                val order = Order(
                    id = newOrderRef.id,
                    userId = userId,
                    userName = userName,
                    items = items,
                    slotId = slotId,
                    slotLabel = slotLabel,
                    pickupCode = pickupCode,
                    status = OrderStatus.RECEIVED.name,
                    total = total,
                    date = date,
                    createdAt = Timestamp.now()
                )
                transaction.set(newOrderRef, order)
                order
            }.await()

            Result.success(createdOrder)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getTodayOrders(date: String): Flow<List<Order>> = callbackFlow {
        val listener = firestore.collection("orders")
            .whereEqualTo("date", date)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val orders = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Order::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.createdAt.seconds } ?: emptyList()
                trySend(orders)
            }

        awaitClose { listener.remove() }
    }

    override fun getUserOrders(userId: String): Flow<List<Order>> = callbackFlow {
        val listener = firestore.collection("orders")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val orders = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Order::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.createdAt.seconds } ?: emptyList()
                trySend(orders)
            }

        awaitClose { listener.remove() }
    }

    override fun getOrderById(orderId: String): Flow<Order?> = callbackFlow {
        val listener = firestore.collection("orders")
            .document(orderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val order = snapshot.toObject(Order::class.java)?.copy(id = snapshot.id)
                    trySend(order)
                } else {
                    trySend(null)
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun updateOrderStatus(
        orderId: String,
        newStatus: OrderStatus
    ): Result<Unit> {
        return try {
            firestore.collection("orders")
                .document(orderId)
                .update("status", newStatus.name)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelOrder(orderId: String): Result<Unit> {
        return try {
            val orderRef = firestore.collection("orders").document(orderId)

            firestore.runTransaction { transaction ->
                val orderDoc = transaction.get(orderRef)
                if (!orderDoc.exists()) {
                    throw FirebaseFirestoreException("Không tìm thấy đơn hàng", FirebaseFirestoreException.Code.NOT_FOUND)
                }
                val order = orderDoc.toObject(Order::class.java)
                    ?: throw FirebaseFirestoreException("Không thể đọc thông tin đơn hàng", FirebaseFirestoreException.Code.ABORTED)

                if (order.status == OrderStatus.CANCELLED.name) {
                    return@runTransaction
                }

                // Decrement slot currentOrders if slot exists
                if (order.slotId.isNotBlank()) {
                    val slotRef = firestore.collection("timeSlots").document(order.slotId)
                    val slotDoc = transaction.get(slotRef)
                    if (slotDoc.exists()) {
                        val currentOrders = slotDoc.getLong("currentOrders") ?: 0L
                        if (currentOrders > 0) {
                            transaction.update(slotRef, "currentOrders", currentOrders - 1)
                        }
                    }
                }

                transaction.update(orderRef, "status", OrderStatus.CANCELLED.name)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verifyAndCompletePickupCode(
        code: String,
        date: String
    ): Result<Order> {
        return try {
            val snapshot = firestore.collection("orders")
                .whereEqualTo("date", date)
                .whereEqualTo("pickupCode", code.trim())
                .get()
                .await()

            if (snapshot.isEmpty) {
                return Result.failure(Exception("Không tìm thấy mã"))
            }

            val doc = snapshot.documents.first()
            val order = doc.toObject(Order::class.java)?.copy(id = doc.id)
                ?: return Result.failure(Exception("Không thể đọc dữ liệu đơn"))

            if (order.status != OrderStatus.READY.name) {
                return Result.failure(Exception("Đơn chưa sẵn sàng"))
            }

            firestore.collection("orders").document(order.id)
                .update("status", OrderStatus.COMPLETED.name)
                .await()

            Result.success(order.copy(status = OrderStatus.COMPLETED.name))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

