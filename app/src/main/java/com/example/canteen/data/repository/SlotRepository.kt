package com.example.canteen.data.repository

import com.example.canteen.data.model.TimeSlot
import kotlinx.coroutines.flow.Flow

interface SlotRepository {
    fun getTimeSlots(): Flow<List<TimeSlot>>
    fun getTimeSlots(date: String): Flow<List<TimeSlot>>
    suspend fun getSlotById(slotId: String): TimeSlot?
}

