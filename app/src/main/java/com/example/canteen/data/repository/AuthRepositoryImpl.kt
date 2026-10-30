package com.example.canteen.data.repository

import com.example.canteen.data.model.User
import com.example.canteen.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    override val currentUserFlow: Flow<User?> = callbackFlow {
        var userListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            userListenerRegistration?.remove()
            userListenerRegistration = null

            if (firebaseUser == null) {
                trySend(null)
            } else {
                userListenerRegistration = firestore.collection("users")
                    .document(firebaseUser.uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            trySend(null)
                            return@addSnapshotListener
                        }
                        if (snapshot != null && snapshot.exists()) {
                            val user = snapshot.toObject(User::class.java)?.copy(uid = snapshot.id)
                            trySend(user)
                        } else {
                            // Document does not exist yet, construct fallback from FirebaseUser
                            val role = if (firebaseUser.email?.startsWith("canteen", ignoreCase = true) == true) {
                                UserRole.CANTEEN.name
                            } else {
                                UserRole.STUDENT.name
                            }
                            val fallbackName = if (role == UserRole.CANTEEN.name) "Căng tin Quầy 1" else "Học sinh Demo"
                            val fallbackUser = User(
                                uid = firebaseUser.uid,
                                name = firebaseUser.displayName ?: fallbackName,
                                email = firebaseUser.email ?: "",
                                role = role
                            )
                            trySend(fallbackUser)
                        }
                    }
            }
        }

        auth.addAuthStateListener(authStateListener)

        awaitClose {
            auth.removeAuthStateListener(authStateListener)
            userListenerRegistration?.remove()
        }
    }

    override suspend fun login(email: String, password: String): Result<User> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = authResult.user ?: throw Exception("Đăng nhập thất bại")

            val doc = firestore.collection("users").document(firebaseUser.uid).get().await()
            val user = if (doc.exists()) {
                doc.toObject(User::class.java)?.copy(uid = doc.id)
                    ?: throw Exception("Không thể đọc thông tin người dùng")
            } else {
                // Determine role based on email pattern
                val role = if (email.startsWith("canteen", ignoreCase = true)) {
                    UserRole.CANTEEN.name
                } else {
                    UserRole.STUDENT.name
                }
                val name = if (role == UserRole.CANTEEN.name) "Căng tin Quầy 1" else "Học sinh Demo"
                val newUser = User(
                    uid = firebaseUser.uid,
                    name = name,
                    email = email.trim(),
                    role = role
                )
                firestore.collection("users").document(firebaseUser.uid).set(newUser).await()
                newUser
            }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        return try {
            val doc = firestore.collection("users").document(firebaseUser.uid).get().await()
            if (doc.exists()) {
                doc.toObject(User::class.java)?.copy(uid = doc.id)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun logout() {
        auth.signOut()
    }

    override suspend fun ensureUserDocument(
        uid: String,
        email: String,
        name: String,
        role: String
    ): Result<Unit> {
        return try {
            val user = User(uid = uid, name = name, email = email, role = role)
            firestore.collection("users").document(uid).set(user).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

