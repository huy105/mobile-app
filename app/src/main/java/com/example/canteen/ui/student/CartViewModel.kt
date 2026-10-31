package com.example.canteen.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.canteen.data.model.Voucher
import com.example.canteen.data.repository.AuthRepository
import com.example.canteen.data.repository.CartRepository
import com.example.canteen.data.repository.CartState
import com.example.canteen.data.repository.VoucherRepository
import com.example.canteen.ui.common.util.PricingCalculator
import com.example.canteen.ui.common.util.PricingResult
import com.example.canteen.ui.common.util.formatVnd
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class CartUiState(
    val cartState: CartState = CartState(),
    val userPoints: Int = 0,
    val availableVouchers: List<Voucher> = emptyList(),
    val appliedVoucher: Voucher? = null,
    val voucherInput: String = "",
    val voucherError: String? = null,
    val pricing: PricingResult = PricingResult(0L, 0L, 0, 0L, 0L, 0L)
) {
    val canUsePoints: Boolean
        get() = userPoints > 0 && cartState.totalPrice > 0L && (pricing.subtotal - pricing.voucherDiscount) >= 500L
}

private data class LocalVoucherState(
    val availableVouchers: List<Voucher> = emptyList(),
    val appliedVoucher: Voucher? = null,
    val voucherInput: String = "",
    val voucherError: String? = null
)

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cartRepository: CartRepository,
    private val voucherRepository: VoucherRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _voucherState = MutableStateFlow(LocalVoucherState())

    val cartState: StateFlow<CartState> = cartRepository.cartState

    private val today: String
        get() = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    val uiState: StateFlow<CartUiState> = combine(
        cartRepository.cartState,
        authRepository.currentUserFlow,
        _voucherState
    ) { cart, user, vState ->
        val userPoints = user?.points ?: 0

        // If subtotal drops below minOrder of applied voucher, notify user
        val effectiveVoucher = if (vState.appliedVoucher != null && vState.appliedVoucher.minOrder > 0 && cart.totalPrice < vState.appliedVoucher.minOrder) {
            null
        } else {
            vState.appliedVoucher
        }

        val effectiveError = if (vState.appliedVoucher != null && vState.appliedVoucher.minOrder > 0 && cart.totalPrice < vState.appliedVoucher.minOrder) {
            "Đơn tối thiểu ${vState.appliedVoucher.minOrder.formatVnd()} để dùng mã này"
        } else {
            vState.voucherError
        }

        val pricing = PricingCalculator.calculate(
            subtotal = cart.totalPrice,
            voucher = effectiveVoucher,
            userPoints = userPoints,
            usePoints = cart.usePoints
        )

        CartUiState(
            cartState = cart,
            userPoints = userPoints,
            availableVouchers = vState.availableVouchers,
            appliedVoucher = effectiveVoucher,
            voucherInput = vState.voucherInput,
            voucherError = effectiveError,
            pricing = pricing
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CartUiState()
    )

    init {
        // Load available vouchers for current user
        viewModelScope.launch {
            combine(
                voucherRepository.getActiveVouchers(),
                authRepository.currentUserFlow
            ) { allVouchers, currentUser ->
                val userId = currentUser?.uid ?: ""
                val curToday = today
                val eligible = mutableListOf<Voucher>()
                for (v in allVouchers) {
                    val notExpired = v.validUntil.isBlank() || v.validUntil >= curToday
                    val hasUses = v.maxUses == 0 || v.usedCount < v.maxUses
                    if (v.active && notExpired && hasUses) {
                        val isRedeemed = if (userId.isNotBlank()) {
                            voucherRepository.isVoucherRedeemed(v.code, userId)
                        } else false
                        if (!isRedeemed) {
                            eligible.add(v)
                        }
                    }
                }
                eligible
            }.collect { list ->
                _voucherState.update { it.copy(availableVouchers = list) }
            }
        }
    }

    fun onVoucherInputChange(text: String) {
        _voucherState.update { it.copy(voucherInput = text.uppercase(), voucherError = null) }
    }

    fun applyVoucher(code: String) {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isBlank()) {
            _voucherState.update { it.copy(voucherError = "Vui lòng nhập mã voucher") }
            return
        }

        // If clicking same already applied voucher, toggle it off
        if (_voucherState.value.appliedVoucher?.code.equals(cleanCode, ignoreCase = true)) {
            removeVoucher()
            return
        }

        viewModelScope.launch {
            val voucher = voucherRepository.getVoucher(cleanCode)
            val currentUser = authRepository.getCurrentUser()
            val userId = currentUser?.uid ?: ""
            val isRedeemed = if (userId.isNotBlank()) {
                voucherRepository.isVoucherRedeemed(cleanCode, userId)
            } else false

            val subtotal = cartRepository.cartState.value.totalPrice
            val validationError = PricingCalculator.validateVoucher(
                voucher = voucher,
                subtotal = subtotal,
                today = today,
                isAlreadyRedeemed = isRedeemed
            )

            if (validationError != null) {
                _voucherState.update {
                    it.copy(
                        voucherError = validationError,
                        appliedVoucher = null
                    )
                }
                cartRepository.setVoucherCode(null)
            } else {
                _voucherState.update {
                    it.copy(
                        voucherError = null,
                        appliedVoucher = voucher,
                        voucherInput = cleanCode
                    )
                }
                cartRepository.setVoucherCode(cleanCode)
            }
        }
    }

    fun removeVoucher() {
        _voucherState.update {
            it.copy(
                appliedVoucher = null,
                voucherInput = "",
                voucherError = null
            )
        }
        cartRepository.setVoucherCode(null)
    }

    fun toggleUsePoints(use: Boolean) {
        cartRepository.setUsePoints(use)
    }

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
        _voucherState.update {
            it.copy(
                appliedVoucher = null,
                voucherInput = "",
                voucherError = null
            )
        }
    }
}
