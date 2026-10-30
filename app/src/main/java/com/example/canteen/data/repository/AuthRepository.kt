package com.example.canteen.data.repository

import com.example.canteen.data.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUserFlow: Flow<User?>
    suspend fun login(email: String, password: String): Result<User>
    suspend fun getCurrentUser(): User?
    fun logout()
    suspend fun ensureUserDocument(uid: String, email: String, name: String, role: String): Result<Unit>
}

