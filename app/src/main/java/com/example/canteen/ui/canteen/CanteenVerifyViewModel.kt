package com.example.canteen.ui.canteen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.canteen.data.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class CanteenVerifyUiState(
    val code: String = "",
    val isVerifying: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val isError: Boolean = false
)

@HiltViewModel
class CanteenVerifyViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CanteenVerifyUiState())
    val uiState: StateFlow<CanteenVerifyUiState> = _uiState.asStateFlow()

    private val todayDate: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun onDigit(digit: Char) {
        if (_uiState.value.isVerifying) return

        val currentCode = _uiState.value.code
        if (currentCode.length < 4) {
            val newCode = currentCode + digit
            _uiState.update {
                it.copy(
                    code = newCode,
                    errorMessage = null,
                    isError = false
                )
            }

            // Auto-check on 4 digits
            if (newCode.length == 4) {
                verifyCode(newCode)
            }
        }
    }

    fun onBackspace() {
        if (_uiState.value.isVerifying) return

        val currentCode = _uiState.value.code
        if (currentCode.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    code = currentCode.dropLast(1),
                    errorMessage = null,
                    isError = false
                )
            }
        }
    }

    fun verifyCode(codeToVerify: String = _uiState.value.code) {
        if (codeToVerify.length != 4 || _uiState.value.isVerifying) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isVerifying = true,
                    errorMessage = null,
                    isError = false
                )
            }

            val result = orderRepository.verifyAndCompletePickupCode(codeToVerify, todayDate)

            result.fold(
                onSuccess = { order ->
                    _uiState.update {
                        it.copy(
                            code = "",
                            isVerifying = false,
                            successMessage = "Đã trả đơn #${order.pickupCode} - ${order.userName}",
                            errorMessage = null,
                            isError = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isVerifying = false,
                            errorMessage = error.message ?: "Mã không hợp lệ",
                            isError = true
                        )
                    }
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.update {
            it.copy(
                successMessage = null,
                errorMessage = null,
                isError = false
            )
        }
    }
}

