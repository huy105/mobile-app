package com.example.canteen.data.repository

import com.example.canteen.data.model.MenuItem
import kotlinx.coroutines.flow.Flow

interface MenuRepository {
    fun getTodayMenuItems(date: String): Flow<List<MenuItem>>
    suspend fun updateStock(menuItemId: String, inStock: Boolean): Result<Unit>
    suspend fun updateQuota(menuItemId: String, dailyQuota: Int): Result<Unit>
    suspend fun getMenuItemById(menuItemId: String): MenuItem?
}

