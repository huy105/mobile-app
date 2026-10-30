package com.example.canteen.data.repository

import com.example.canteen.data.model.MenuItem
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MenuRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : MenuRepository {

    override fun getTodayMenuItems(date: String): Flow<List<MenuItem>> = callbackFlow {
        val listener = firestore.collection("menuItems")
            .whereEqualTo("date", date)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(MenuItem::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(items)
            }

        awaitClose { listener.remove() }
    }

    override suspend fun updateStock(menuItemId: String, inStock: Boolean): Result<Unit> {
        return try {
            firestore.collection("menuItems")
                .document(menuItemId)
                .update("inStock", inStock)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMenuItemById(menuItemId: String): MenuItem? {
        return try {
            val doc = firestore.collection("menuItems").document(menuItemId).get().await()
            if (doc.exists()) {
                doc.toObject(MenuItem::class.java)?.copy(id = doc.id)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

