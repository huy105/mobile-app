package com.example.canteen.data.repository

import android.content.Context
import com.example.canteen.data.model.MenuItem
import com.example.canteen.data.model.TimeSlot
import com.example.canteen.data.model.User
import com.example.canteen.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

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
                    val menuItem = MenuItem(
                        id = docRef.id,
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        price = obj.getLong("price"),
                        category = obj.getString("category"),
                        inStock = obj.optBoolean("inStock", true),
                        imageUrl = obj.optString("imageUrl", ""),
                        date = today
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

            // 4. Try ensuring demo accounts if needed
            seedDemoUser("student@demo.com", "123456", "Học sinh Demo", UserRole.STUDENT.name)
            seedDemoUser("canteen@demo.com", "123456", "Căng tin Quầy 1", UserRole.CANTEEN.name)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun seedDemoUser(email: String, pass: String, name: String, role: String) {
        try {
            // Attempt to register if not exists
            val result = auth.createUserWithEmailAndPassword(email, pass).await()
            val uid = result.user?.uid
            if (uid != null) {
                firestore.collection("users").document(uid).set(
                    User(uid = uid, name = name, email = email, role = role)
                ).await()
            }
        } catch (_: Exception) {
            // Account already created or permission denied, ignore
        }
    }
}

