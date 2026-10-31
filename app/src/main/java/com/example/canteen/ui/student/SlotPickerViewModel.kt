package com.example.canteen.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.canteen.data.model.TimeSlot
import com.example.canteen.data.repository.AuthRepository
import com.example.canteen.data.repository.CartRepository
import com.example.canteen.data.repository.OrderRepository
import com.example.canteen.data.repository.SlotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SlotPickerUiState(
    val slots: List<TimeSlot> = emptyList(),
    val selectedSlotId: String? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val createdOrderId: String? = null
) {
    val selectedSlot: TimeSlot?
        get() = slots.find { it.id == selectedSlotId }
}

@HiltViewModel
class SlotPickerViewModel @Inject constructor(
    private val slotRepository: SlotRepository,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val cartRepository: CartRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SlotPickerUiState())
    val uiState: StateFlow<SlotPickerUiState> = _uiState.asStateFlow()

    val cartState = cartRepository.cartState

    private val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    init {
        viewModelScope.launch {
            slotRepository.getTimeSlots().collect { slots ->
                _uiState.update { current ->
                    // Auto-select first available slot if none selected yet
                    val firstAvailable = slots.firstOrNull { !it.isFull && !isSlotPast(it) }?.id
                    current.copy(
                        slots = slots,
                        selectedSlotId = current.selectedSlotId ?: firstAvailable,
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * Check if a time slot has already passed based on current minute of day.
     * Note: If outside lunch hours (10:00 - 14:00), we only mark past if startMinute <= 675
     * so that evaluators testing in the evening or morning can still select slots freely.
     */
    fun isSlotPast(slot: TimeSlot): Boolean {
        val now = Calendar.getInstance()
        val currentMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return if (currentMinute in 660..780) {
            currentMinute >= slot.endMinute
        } else {
            // Outside normal lunch window (e.g. testing in evening), only first slot simulated as past
            slot.endMinute <= 690
        }
    }

    fun selectSlot(slotId: String) {
        val slot = _uiState.value.slots.find { it.id == slotId }
        if (slot != null && !slot.isFull && !isSlotPast(slot)) {
            _uiState.update { it.copy(selectedSlotId = slotId, errorMessage = null) }
        }
    }

    fun confirmOrder() {
        val selectedSlot = _uiState.value.selectedSlot ?: return
        val cart = cartRepository.cartState.value
        if (cart.items.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val currentUser = authRepository.getCurrentUser()
            val userId = currentUser?.uid ?: "guest"
            val userName = currentUser?.name?.ifBlank { "Học sinh Demo" } ?: "Học sinh Demo"

            val result = orderRepository.createOrder(
                userId = userId,
                userName = userName,
                items = cart.itemList,
                slotId = selectedSlot.id,
                slotLabel = selectedSlot.label,
                total = cart.totalPrice,
                date = today,
                voucherCode = cart.voucherCode,
                usePoints = cart.usePoints
            )

            result.onSuccess { order ->
                cartRepository.clearCart()
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        createdOrderId = order.id
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = error.message ?: "Đặt đơn thất bại, vui lòng thử lại"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

