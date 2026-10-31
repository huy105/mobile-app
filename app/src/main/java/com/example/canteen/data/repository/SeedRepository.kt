package com.example.canteen.data.repository

interface SeedRepository {
    suspend fun seedIfEmpty(): Result<Unit>
    suspend fun seedMissingMenuItems(): Result<Unit>
    suspend fun grantStartingPointsIfMissing(uid: String): Result<Unit>
}

