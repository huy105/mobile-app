package com.example.canteen.data.repository

import com.example.canteen.data.model.TimeSlot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SlotRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : SlotRepository {

    override fun getTimeSlots(): Flow<List<TimeSlot>> = callbackFlow {
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

