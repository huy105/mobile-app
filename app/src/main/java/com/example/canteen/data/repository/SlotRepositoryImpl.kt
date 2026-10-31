package com.example.canteen.data.repository

import com.example.canteen.data.model.TimeSlot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SlotRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : SlotRepository {

    /** Slots with `currentOrders` taken from today's counters, so capacity resets every day. */
    override fun getTimeSlots(): Flow<List<TimeSlot>> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return combine(slotDefinitions(), todayCounters(today)) { slots, counters ->
            slots.map { it.copy(currentOrders = counters[it.id] ?: 0) }
        }
    }

    private fun todayCounters(date: String): Flow<Map<String, Int>> = callbackFlow {
        val listener = firestore.collection("timeSlotCounters")
            .whereEqualTo("date", date)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val counters = snapshot?.documents?.associate { doc ->
                    (doc.getString("slotId") ?: "") to (doc.getLong("count") ?: 0L).toInt()
                } ?: emptyMap()
                trySend(counters)
            }

        awaitClose { listener.remove() }
    }

    private fun slotDefinitions(): Flow<List<TimeSlot>> = callbackFlow {
        val listener = firestore.collection("timeSlots")
            .orderBy("startMinute")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val slots = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(TimeSlot::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(slots)
            }

        awaitClose { listener.remove() }
    }

    override suspend fun getSlotById(slotId: String): TimeSlot? {
        return try {
            val doc = firestore.collection("timeSlots").document(slotId).get().await()
            if (doc.exists()) {
                doc.toObject(TimeSlot::class.java)?.copy(id = doc.id)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

