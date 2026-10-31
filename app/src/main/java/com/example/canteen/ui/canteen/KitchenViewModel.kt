package com.example.canteen.ui.canteen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.canteen.data.repository.MenuRepository
import com.example.canteen.data.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class KitchenUiState(
    val isLoading: Boolean = true,
    val selectedDate: LocalDate = LocalDate.now(),
    val isToday: Boolean = true,
    val summary: KitchenSummary = KitchenSummary(),
    val errorMessage: String? = null
) {
    val hasOrders: Boolean
        get() = summary.totalPortions > 0
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KitchenViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val menuRepository: MenuRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _uiState = MutableStateFlow(KitchenUiState())
    val uiState: StateFlow<KitchenUiState> = _uiState.asStateFlow()

    init {
        loadKitchenData()
    }

    fun selectDate(date: LocalDate) {
        val today = LocalDate.now()
        val target = if (date.isAfter(today)) today else date
        _selectedDate.value = target
    }

    fun previousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    fun nextDay() {
        val today = LocalDate.now()
        val current = _selectedDate.value
        if (current.isBefore(today)) {
            val next = current.plusDays(1)
            if (!next.isAfter(today)) {
                _selectedDate.value = next
            }
        }
    }

    fun resetToToday() {
        _selectedDate.value = LocalDate.now()
    }

    private fun loadKitchenData() {
        viewModelScope.launch {
            _selectedDate.flatMapLatest { date ->
                val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val isToday = date.isEqual(LocalDate.now())

                combine(
                    orderRepository.getTodayOrders(dateStr),
                    menuRepository.getTodayMenuItems(dateStr)
                ) { orders, menuItems ->
                    val summary = KitchenCalculator.calculate(orders, menuItems)
                    KitchenUiState(
                        isLoading = false,
                        selectedDate = date,
                        isToday = isToday,
                        summary = summary,
                        errorMessage = null
                    )
                }
            }.catch { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Đã xảy ra lỗi khi tải dữ liệu nhà bếp"
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}

