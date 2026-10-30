package com.example.canteen.data.model

enum class UserRole {
    STUDENT,
    CANTEEN;

    companion object {
        fun fromString(role: String?): UserRole {
            return entries.firstOrNull { it.name.equals(role, ignoreCase = true) } ?: STUDENT
        }
    }
}

