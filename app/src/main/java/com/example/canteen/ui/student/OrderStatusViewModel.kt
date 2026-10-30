package com.example.canteen.ui.student

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.canteen.data.model.Order
import com.example.canteen.data.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrderStatusUiState(
    val order: Order? = null,
    val isLoading: Boolean = true,
    val isCancelling: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class OrderStatusViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderStatusUiState())
    val uiState: StateFlow<OrderStatusUiState> = _uiState.asStateFlow()

    private val orderId: String? = savedStateHandle["orderId"]

    init {
        if (orderId != null) {
            observeOrder(orderId)
        }
    }

    fun setOrderId(id: String) {
        observeOrder(id)
    }

    private fun observeOrder(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            orderRepository.getOrderById(id).collect { order ->
                _uiState.update {
                    it.copy(
                        order = order,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun cancelOrder() {
        val currentOrder = _uiState.value.order ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isCancelling = true) }
            val result = orderRepository.cancelOrder(currentOrder.id)
            result.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isCancelling = false,
                        errorMessage = error.message ?: "Không thể hủy đơn hàng"
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isCancelling = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

