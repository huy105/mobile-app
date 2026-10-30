package com.example.canteen.data.repository

import com.example.canteen.data.model.MenuItem
import com.example.canteen.data.model.OrderItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class CartState(
    val items: Map<String, OrderItem> = emptyMap()
) {
    val itemList: List<OrderItem>
        get() = items.values.toList()

    val totalCount: Int
        get() = items.values.sumOf { it.quantity }

    val totalPrice: Long
        get() = items.values.sumOf { it.subtotal }
}

interface CartRepository {
    val cartState: StateFlow<CartState>
    fun addItem(item: MenuItem)
    fun increaseQuantity(menuItemId: String)
    fun decreaseQuantity(menuItemId: String)
    fun removeItem(menuItemId: String)
    fun clearCart()
}

@Singleton
class CartRepositoryImpl @Inject constructor() : CartRepository {

    private val _cartState = MutableStateFlow(CartState())
    override val cartState: StateFlow<CartState> = _cartState.asStateFlow()

    override fun addItem(item: MenuItem) {
        if (!item.inStock) return
        _cartState.update { current ->
            val updated = current.items.toMutableMap()
            val existing = updated[item.id]
            if (existing != null) {
                updated[item.id] = existing.copy(quantity = existing.quantity + 1)
            } else {
                updated[item.id] = OrderItem(
                    menuItemId = item.id,
                    name = item.name,
                    price = item.price,
                    quantity = 1
                )
            }
            current.copy(items = updated)
        }
    }

    override fun increaseQuantity(menuItemId: String) {
        _cartState.update { current ->
            val updated = current.items.toMutableMap()
            val existing = updated[menuItemId] ?: return@update current
            updated[menuItemId] = existing.copy(quantity = existing.quantity + 1)
            current.copy(items = updated)
        }
    }

    override fun decreaseQuantity(menuItemId: String) {
        _cartState.update { current ->
            val updated = current.items.toMutableMap()
            val existing = updated[menuItemId] ?: return@update current
            if (existing.quantity > 1) {
                updated[menuItemId] = existing.copy(quantity = existing.quantity - 1)
            } else {
                updated.remove(menuItemId)
            }
            current.copy(items = updated)
        }
    }

    override fun removeItem(menuItemId: String) {
        _cartState.update { current ->
            val updated = current.items.toMutableMap()
            updated.remove(menuItemId)
            current.copy(items = updated)
        }
    }

    override fun clearCart() {
        _cartState.value = CartState()
    }
}

