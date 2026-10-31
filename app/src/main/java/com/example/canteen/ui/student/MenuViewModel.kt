package com.example.canteen.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.canteen.data.model.MenuItem
import com.example.canteen.data.repository.CartRepository
import com.example.canteen.data.repository.CartState
import com.example.canteen.data.repository.MenuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class MenuUiState(
    val items: List<MenuItem> = emptyList(),
    val selectedCategory: String = "Tất cả",
    val cartState: CartState = CartState(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val categories: List<String> = listOf("Tất cả", "Cơm", "Bún/Mì", "Ăn nhanh", "Đồ uống")

    val filteredItems: List<MenuItem>
        get() = if (selectedCategory == "Tất cả") {
            items
        } else {
            items.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }

    val groupedItems: Map<String, List<MenuItem>>
        get() = if (selectedCategory == "Tất cả") {
            filteredItems.groupBy { it.category }
        } else {
            mapOf(selectedCategory to filteredItems)
        }
}

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val menuRepository: MenuRepository,
    private val cartRepository: CartRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow("Tất cả")
    private val _isLoading = MutableStateFlow(true)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    val uiState: StateFlow<MenuUiState> = combine(
        menuRepository.getTodayMenuItems(today),
        _selectedCategory,
        cartRepository.cartState,
        _isLoading,
        _errorMessage
    ) { items, category, cart, loading, error ->
        MenuUiState(
            items = items,
            selectedCategory = category,
            cartState = cart,
            isLoading = loading && items.isEmpty(),
            errorMessage = error
        )
    }.catch { e ->
        _errorMessage.value = e.message ?: "Lỗi tải thực đơn"
        _isLoading.value = false
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MenuUiState()
    )

    init {
        // Clear loading after first emission is ready
        viewModelScope.launch {
            menuRepository.getTodayMenuItems(today).collect {
                _isLoading.value = false
            }
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun addToCart(item: MenuItem) {
        if (!item.inStock) return
        val remaining = item.remainingQty
        if (remaining != null) {
            val inCart = cartRepository.cartState.value.items[item.id]?.quantity ?: 0
            if (inCart >= remaining) {
                _errorMessage.value = "Chỉ còn $remaining suất"
                return
            }
        }
        cartRepository.addItem(item)
    }

    fun increaseQuantity(menuItemId: String) {
        val item = uiState.value.items.find { it.id == menuItemId }
        val remaining = item?.remainingQty
        if (remaining != null) {
            val inCart = cartRepository.cartState.value.items[menuItemId]?.quantity ?: 0
            if (inCart >= remaining) {
                _errorMessage.value = "Chỉ còn $remaining suất"
                return
            }
        }
        cartRepository.increaseQuantity(menuItemId)
    }

    fun decreaseQuantity(menuItemId: String) {
        cartRepository.decreaseQuantity(menuItemId)
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

