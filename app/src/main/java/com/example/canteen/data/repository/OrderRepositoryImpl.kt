package com.example.canteen.data.repository

import com.example.canteen.data.model.CancelReason
import com.example.canteen.data.model.Order
import com.example.canteen.data.model.OrderItem
import com.example.canteen.data.model.OrderStatus
import com.example.canteen.data.model.TimeSlot
import com.example.canteen.data.model.slotCounterId
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
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
            // 1. Generate unique 4-digit code for today
            val existingCodes = firestore.collection("orders")
                .whereEqualTo("date", date)
                .get()
                .await()
                .documents.mapNotNull { it.getString("pickupCode") }.toSet()

            var pickupCode: String
            do {
                pickupCode = String.format(Locale.getDefault(), "%04d", Random.nextInt(10000))
            } while (pickupCode in existingCodes)

            // 2. Execute Firestore transaction for stock, quota and slot capacity validation
            val slotRef = firestore.collection("timeSlots").document(slotId)
            val counterRef = firestore.collection("timeSlotCounters").document(slotCounterId(slotId, date))
            val newOrderRef = firestore.collection("orders").document()

            val createdOrder = firestore.runTransaction { transaction ->
                // Check TimeSlot
                val slotDoc = transaction.get(slotRef)
                if (!slotDoc.exists()) {
                    throw FirebaseFirestoreException("Khung giờ không tồn tại", FirebaseFirestoreException.Code.NOT_FOUND)
                }
                val slot = slotDoc.toObject(TimeSlot::class.java)
                    ?: throw FirebaseFirestoreException("Không thể đọc thông tin khung giờ", FirebaseFirestoreException.Code.ABORTED)

                // Capacity is counted per day, not on the slot document itself
                val counterDoc = transaction.get(counterRef)
                val ordersToday = counterDoc.getLong("count") ?: 0L
                if (ordersToday >= slot.maxOrders) {
                    throw FirebaseFirestoreException("Khung giờ đã đầy", FirebaseFirestoreException.Code.ABORTED)
                }

                // Read all menu item docs before any writes
                val itemDocPairs = items.mapNotNull { item ->
                    if (item.menuItemId.isNotBlank()) {
                        val itemRef = firestore.collection("menuItems").document(item.menuItemId)
                        val itemDoc = transaction.get(itemRef)
                        Triple(item, itemRef, itemDoc)
                    } else null
                }

                // Check menu items in stock and quota
                for ((item, _, itemDoc) in itemDocPairs) {
                    if (itemDoc.exists()) {
                        val inStock = itemDoc.getBoolean("inStock") ?: true
                        if (!inStock) {
                            throw FirebaseFirestoreException("Món ${item.name} đã hết hàng", FirebaseFirestoreException.Code.ABORTED)
                        }
                        val dailyQuota = (itemDoc.getLong("dailyQuota") ?: 0L).toInt()
                        val soldQty = (itemDoc.getLong("soldQty") ?: 0L).toInt()
                        if (dailyQuota > 0 && soldQty + item.quantity > dailyQuota) {
                            val remaining = (dailyQuota - soldQty).coerceAtLeast(0)
                            throw FirebaseFirestoreException("Món ${item.name} chỉ còn $remaining suất", FirebaseFirestoreException.Code.ABORTED)
                        }
                    }
                }

                // Writes start here:
                // Increment today's counter for this slot
                transaction.set(
                    counterRef,
                    mapOf("slotId" to slotId, "date" to date, "count" to ordersToday + 1)
                )

                // Update soldQty and inStock for items
                for ((item, itemRef, itemDoc) in itemDocPairs) {
                    if (itemDoc.exists()) {
                        val dailyQuota = (itemDoc.getLong("dailyQuota") ?: 0L).toInt()
                        val soldQty = (itemDoc.getLong("soldQty") ?: 0L).toInt()
                        val newSoldQty = soldQty + item.quantity
                        val updates = mutableMapOf<String, Any>("soldQty" to newSoldQty)
                        if (dailyQuota > 0 && newSoldQty >= dailyQuota) {
                            updates["inStock"] = false
                        }
                        transaction.update(itemRef, updates)
                    }
                }

                // Create Order
                val now = Timestamp.now()
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
                    createdAt = now,
                    updatedAt = now
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
            val now = Timestamp.now()
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus.name,
                "updatedAt" to now
            )
            when (newStatus) {
                OrderStatus.PREPARING -> updates["preparingAt"] = now
                OrderStatus.READY -> updates["readyAt"] = now
                OrderStatus.COMPLETED -> updates["completedAt"] = now
                else -> {}
            }
            firestore.collection("orders")
                .document(orderId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelOrder(
        orderId: String,
        cancelledBy: String,
        reason: CancelReason
    ): Result<Unit> {
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

                // 1. Read all menuItem docs for items in the order BEFORE any writes
                val menuItemDocs = order.items.mapNotNull { item ->
                    if (item.menuItemId.isNotBlank()) {
                        val ref = firestore.collection("menuItems").document(item.menuItemId)
                        val snap = transaction.get(ref)
                        Triple(item, ref, snap)
                    } else null
                }

                // 2. Read counterDoc
                val counterRef = if (order.slotId.isNotBlank() && order.date.isNotBlank()) {
                    firestore.collection("timeSlotCounters").document(slotCounterId(order.slotId, order.date))
                } else null
                val counterDoc = counterRef?.let { transaction.get(it) }

                // Writes start here:
                // Give the seat back to the order's own day
                if (counterRef != null && counterDoc != null && counterDoc.exists()) {
                    val count = counterDoc.getLong("count") ?: 0L
                    if (count > 0) {
                        transaction.update(counterRef, "count", count - 1)
                    }
                }

                // Restore soldQty if reason != SOLD_OUT
                if (reason != CancelReason.SOLD_OUT) {
                    for ((item, ref, snap) in menuItemDocs) {
                        if (snap.exists()) {
                            val dailyQuota = (snap.getLong("dailyQuota") ?: 0L).toInt()
                            val soldQty = (snap.getLong("soldQty") ?: 0L).toInt()
                            if (dailyQuota > 0) {
                                val newSoldQty = maxOf(0, soldQty - item.quantity)
                                transaction.update(ref, "soldQty", newSoldQty)
                            }
                        }
                    }
                }

                val now = Timestamp.now()
                transaction.update(
                    orderRef,
                    mapOf(
                        "status" to OrderStatus.CANCELLED.name,
                        "cancelledAt" to now,
                        "updatedAt" to now,
                        "cancelledBy" to cancelledBy,
                        "cancelReason" to reason.name
                    )
                )
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

            val now = Timestamp.now()
            firestore.collection("orders").document(order.id)
                .update(
                    mapOf(
                        "status" to OrderStatus.COMPLETED.name,
                        "completedAt" to now,
                        "updatedAt" to now
                    )
                )
                .await()

            Result.success(order.copy(
                status = OrderStatus.COMPLETED.name,
                completedAt = now,
                updatedAt = now
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
