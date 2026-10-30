package com.example.canteen.data.model

import com.google.firebase.firestore.DocumentId

data class MenuItem(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Long = 0L,
    val imageUrl: String = "",
    val category: String = "",
    val inStock: Boolean = true,
    val date: String = "" // format yyyy-MM-dd
)

