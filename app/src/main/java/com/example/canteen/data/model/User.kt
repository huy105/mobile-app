package com.example.canteen.data.model

import com.google.firebase.firestore.DocumentId

data class User(
    @DocumentId
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = UserRole.STUDENT.name
) {
    val userRole: UserRole
        get() = UserRole.fromString(role)
}

