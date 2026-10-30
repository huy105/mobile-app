package com.example.canteen.ui.student

import androidx.lifecycle.ViewModel
import com.example.canteen.data.repository.CartRepository
import com.example.canteen.data.repository.CartState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cartRepository: CartRepository
) : ViewModel() {

    val cartState: StateFlow<CartState> = cartRepository.cartState

    fun increaseQuantity(menuItemId: String) {
        cartRepository.increaseQuantity(menuItemId)
    }

    fun decreaseQuantity(menuItemId: String) {
        cartRepository.decreaseQuantity(menuItemId)
    }

    fun removeItem(menuItemId: String) {
        cartRepository.removeItem(menuItemId)
    }

    fun clearCart() {
        cartRepository.clearCart()
    }
}

