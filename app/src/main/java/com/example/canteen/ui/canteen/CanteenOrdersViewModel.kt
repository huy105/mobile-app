package com.example.canteen.ui.canteen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.canteen.data.model.CancelReason
import com.example.canteen.data.model.Order
import com.example.canteen.data.model.OrderStatus
import com.example.canteen.data.model.TimeSlot
import com.example.canteen.data.repository.OrderRepository
import com.example.canteen.data.repository.SlotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class CanteenOrdersUiState(
    val orders: List<Order> = emptyList(),
    val slots: List<TimeSlot> = emptyList(),
    val selectedSlotId: String? = null, // null = "Tất cả"
    val isLoading: Boolean = true,
    val isActionInProgress: Boolean = false,
    val errorMessage: String? = null
) {
    // Only show active orders in kanban (RECEIVED, PREPARING, READY)
    // COMPLETED and CANCELLED are hidden per design note
    val activeOrders: List<Order>
        get() = orders.filter {
            it.status == OrderStatus.RECEIVED.name ||
                it.status == OrderStatus.PREPARING.name ||
                it.status == OrderStatus.READY.name
        }

    // Counts per slot for chip badges
    val slotOrderCounts: Map<String, Int>
        get() = activeOrders.groupBy { it.slotId }.mapValues { it.value.size }

    // Filter by selected slot
    val filteredOrders: List<Order>
        get() = if (selectedSlotId == null) {
            activeOrders
        } else {
            activeOrders.filter { it.slotId == selectedSlotId }
        }

    val receivedOrders: List<Order>
        get() = filteredOrders.filter { it.status == OrderStatus.RECEIVED.name }

    val preparingOrders: List<Order>
        get() = filteredOrders.filter { it.status == OrderStatus.PREPARING.name }

    val readyOrders: List<Order>
        get() = filteredOrders.filter { it.status == OrderStatus.READY.name }
}

@HiltViewModel
class CanteenOrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val slotRepository: SlotRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CanteenOrdersUiState())
    val uiState: StateFlow<CanteenOrdersUiState> = _uiState.asStateFlow()

    private val todayDate: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val date = todayDate

            combine(
                orderRepository.getTodayOrders(date),
                slotRepository.getTimeSlots()
            ) { orders, slots ->
                Pair(orders, slots)
            }.catch { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Lỗi khi tải danh sách đơn hàng"
                    )
                }
            }.collect { (orders, slots) ->
                _uiState.update {
                    it.copy(
                        orders = orders,
                        slots = slots,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun selectSlot(slotId: String?) {
        _uiState.update { it.copy(selectedSlotId = slotId) }
    }

    fun advanceStatus(order: Order) {
        val nextStatus = when (order.status) {
            OrderStatus.RECEIVED.name -> OrderStatus.PREPARING
            OrderStatus.PREPARING.name -> OrderStatus.READY
            OrderStatus.READY.name -> OrderStatus.COMPLETED
            else -> return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true) }
            val result = orderRepository.updateOrderStatus(order.id, nextStatus)
            _uiState.update {
                it.copy(
                    isActionInProgress = false,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun cancelOrder(orderId: String, reason: CancelReason) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true) }
            val result = orderRepository.cancelOrder(orderId, "CANTEEN", reason)
            _uiState.update {
                it.copy(
                    isActionInProgress = false,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

