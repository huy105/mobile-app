package com.example.canteen.ui.canteen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.canteen.data.model.MenuItem
import com.example.canteen.data.repository.MenuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class CanteenMenuUiState(
    val items: List<MenuItem> = emptyList(),
    val isLoading: Boolean = true,
    val isToggling: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class CanteenMenuViewModel @Inject constructor(
    private val menuRepository: MenuRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CanteenMenuUiState())
    val uiState: StateFlow<CanteenMenuUiState> = _uiState.asStateFlow()

    private val todayDate: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    init {
        observeMenu()
    }

    private fun observeMenu() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val date = todayDate

            menuRepository.getTodayMenuItems(date)
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Lỗi khi tải danh sách món ăn"
                        )
                    }
                }
                .collect { items ->
                    _uiState.update {
                        it.copy(
                            items = items,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun toggleStock(item: MenuItem) {
        viewModelScope.launch {
            val newStockStatus = !item.inStock
            val result = menuRepository.updateStock(item.id, newStockStatus)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(errorMessage = result.exceptionOrNull()?.message ?: "Lỗi khi cập nhật trạng thái món")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

