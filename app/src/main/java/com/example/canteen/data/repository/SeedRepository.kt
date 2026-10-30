package com.example.canteen.data.repository

interface SeedRepository {
    suspend fun seedIfEmpty(): Result<Unit>
}

