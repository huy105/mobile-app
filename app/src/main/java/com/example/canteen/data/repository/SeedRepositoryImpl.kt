package com.example.canteen.data.repository

import android.content.Context
import com.example.canteen.data.model.MenuItem
import com.example.canteen.data.model.TimeSlot
import com.example.canteen.data.model.User
import com.example.canteen.data.model.UserRole
import com.example.canteen.data.model.Voucher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : SeedRepository {

    override suspend fun seedIfEmpty(): Result<Unit> {
        return try {
            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            // 1. Read seed-menu.json from assets
            val jsonString = context.assets.open("seed-menu.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)

            // 2. Check and seed MenuItems for today
            val todayMenuSnapshot = firestore.collection("menuItems")
                .whereEqualTo("date", today)
                .limit(1)
                .get()
                .await()

            if (todayMenuSnapshot.isEmpty) {
                val menuArray = root.getJSONArray("menuItems")
                val batch = firestore.batch()

                for (i in 0 until menuArray.length()) {
                    val obj = menuArray.getJSONObject(i)
                    val docRef = firestore.collection("menuItems").document()
                    val tagsArray = obj.optJSONArray("tags")
                    val tagsList = mutableListOf<String>()
                    if (tagsArray != null) {
                        for (t in 0 until tagsArray.length()) {
                            tagsList.add(tagsArray.getString(t))
                        }
                    }
                    val menuItem = MenuItem(
                        id = docRef.id,
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        price = obj.getLong("price"),
                        category = obj.getString("category"),
                        inStock = obj.optBoolean("inStock", true),
                        imageUrl = obj.optString("imageUrl", ""),
                        date = today,
                        dishKey = obj.optString("dishKey", ""),
                        tags = tagsList,
                        dailyQuota = obj.optInt("dailyQuota", 0),
                        soldQty = 0,
                        costPrice = obj.optLong("costPrice", 0L)
                    )
                    batch.set(docRef, menuItem)
                }
                batch.commit().await()
            }

            // 3. Check and seed TimeSlots
            val slotsSnapshot = firestore.collection("timeSlots")
                .limit(1)
                .get()
                .await()

            if (slotsSnapshot.isEmpty) {
                val slotsArray = root.getJSONArray("timeSlots")
                val batch = firestore.batch()

                for (i in 0 until slotsArray.length()) {
                    val obj = slotsArray.getJSONObject(i)
                    val docRef = firestore.collection("timeSlots").document()
                    val slot = TimeSlot(
                        id = docRef.id,
                        label = obj.getString("label"),
                        startMinute = obj.getInt("startMinute"),
                        endMinute = obj.getInt("endMinute"),
                        maxOrders = obj.optInt("maxOrders", 10),
                        currentOrders = 0
                    )
                    batch.set(docRef, slot)
                }
                batch.commit().await()
            }

            // 4. Check and seed Vouchers
            seedVouchersIfEmpty(root)

            // 5. Try ensuring demo accounts if needed
            seedDemoUser("student@demo.com", "123456", "Học sinh Demo", UserRole.STUDENT.name)
            seedDemoUser("canteen@demo.com", "123456", "Căng tin Quầy 1", UserRole.CANTEEN.name)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun seedMissingMenuItems(): Result<Unit> {
        return try {
            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val jsonString = context.assets.open("seed-menu.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val menuArray = root.getJSONArray("menuItems")

            val todayDocs = firestore.collection("menuItems")
                .whereEqualTo("date", today)
                .get()
                .await()

            val existingByDishKey = todayDocs.documents.associateBy { it.getString("dishKey") ?: "" }
            val batch = firestore.batch()
            var hasUpdates = false

            for (i in 0 until menuArray.length()) {
                val obj = menuArray.getJSONObject(i)
                val dishKey = obj.optString("dishKey", "")
                val costPrice = obj.optLong("costPrice", 0L)
                val existingDoc = existingByDishKey[dishKey]

                if (existingDoc == null) {
                    val docRef = firestore.collection("menuItems").document()
                    val tagsArray = obj.optJSONArray("tags")
                    val tagsList = mutableListOf<String>()
                    if (tagsArray != null) {
                        for (t in 0 until tagsArray.length()) {
                            tagsList.add(tagsArray.getString(t))
                        }
                    }
                    val item = MenuItem(
                        id = docRef.id,
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        price = obj.getLong("price"),
                        category = obj.getString("category"),
                        inStock = obj.optBoolean("inStock", true),
                        imageUrl = obj.optString("imageUrl", ""),
                        date = today,
                        dishKey = dishKey,
                        tags = tagsList,
                        dailyQuota = obj.optInt("dailyQuota", 0),
                        soldQty = 0,
                        costPrice = costPrice
                    )
                    batch.set(docRef, item)
                    hasUpdates = true
                } else {
                    val currentCostPrice = existingDoc.getLong("costPrice") ?: 0L
                    if (currentCostPrice == 0L && costPrice > 0L) {
                        batch.update(existingDoc.reference, "costPrice", costPrice)
                        hasUpdates = true
                    }
                }
            }

            // Seed vouchers if missing
            seedVouchersIfEmpty(root)

            if (hasUpdates) {
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun grantStartingPointsIfMissing(uid: String): Result<Unit> {
        return try {
            val docRef = firestore.collection("users").document(uid)
            val snapshot = docRef.get().await()
            if (snapshot.exists() && !snapshot.contains("points")) {
                docRef.update("points", 100).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun seedVouchersIfEmpty(root: JSONObject) {
        val vouchersSnapshot = firestore.collection("vouchers")
            .limit(1)
            .get()
            .await()

        if (vouchersSnapshot.isEmpty && root.has("vouchers")) {
            val vouchersArray = root.getJSONArray("vouchers")
            val batch = firestore.batch()

            for (i in 0 until vouchersArray.length()) {
                val obj = vouchersArray.getJSONObject(i)
                val code = obj.getString("code").uppercase()
                val validDays = obj.getInt("validDays")
                val validUntil = LocalDate.now().plusDays(validDays.toLong())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                val voucher = Voucher(
                    code = code,
                    title = obj.getString("title"),
                    type = obj.getString("type"),
                    value = obj.getLong("value"),
                    minOrder = obj.optLong("minOrder", 0L),
                    maxDiscount = obj.optLong("maxDiscount", 0L),
                    validUntil = validUntil,
                    maxUses = obj.optInt("maxUses", 0),
                    usedCount = 0,
                    active = obj.optBoolean("active", true)
                )
                batch.set(firestore.collection("vouchers").document(code), voucher)
            }
            batch.commit().await()
        }
    }

    private suspend fun seedDemoUser(email: String, pass: String, name: String, role: String) {
        try {
            val signInResult = try {
                auth.signInWithEmailAndPassword(email, pass).await()
            } catch (e: Exception) {
                null
            }

            val uid = signInResult?.user?.uid ?: run {
                val createResult = auth.createUserWithEmailAndPassword(email, pass).await()
                createResult.user?.uid
            }

            if (uid != null) {
                val userDoc = firestore.collection("users").document(uid).get().await()
                if (!userDoc.exists()) {
                    val user = User(
                        uid = uid,
                        name = name,
                        email = email,
                        role = role,
                        points = if (role == UserRole.STUDENT.name) 100 else 0
                    )
                    firestore.collection("users").document(uid).set(user).await()
                } else if (!userDoc.contains("points") && role == UserRole.STUDENT.name) {
                    firestore.collection("users").document(uid).update("points", 100).await()
                }
            }
        } catch (e: Exception) {
            // Ignore auth/network errors during demo seeding
        }
    }
}
