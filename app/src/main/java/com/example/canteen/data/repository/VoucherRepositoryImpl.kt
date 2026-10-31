package com.example.canteen.data.repository

import com.example.canteen.data.model.Voucher
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoucherRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : VoucherRepository {

    override fun getActiveVouchers(): Flow<List<Voucher>> = callbackFlow {
        val listener = firestore.collection("vouchers")
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val vouchers = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Voucher::class.java)?.copy(code = doc.id)
                } ?: emptyList()
                trySend(vouchers)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getVoucher(code: String): Voucher? {
        return try {
            val doc = firestore.collection("vouchers").document(code.uppercase().trim()).get().await()
            if (doc.exists()) {
                doc.toObject(Voucher::class.java)?.copy(code = doc.id)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun isVoucherRedeemed(code: String, userId: String): Boolean {
        return try {
            val docId = "${code.uppercase().trim()}_$userId"
            val doc = firestore.collection("voucherRedemptions").document(docId).get().await()
            doc.exists()
        } catch (e: Exception) {
            false
        }
    }
}

