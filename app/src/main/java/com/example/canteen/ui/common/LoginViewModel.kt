package com.example.canteen.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.canteen.data.model.User
import com.example.canteen.data.model.UserRole
import com.example.canteen.data.repository.AuthRepository
import com.example.canteen.data.repository.SeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "student@demo.com",
    val password: String = "123456",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface LoginEvent {
    data class LoginSuccess(val user: User) : LoginEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val seedRepository: SeedRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<LoginEvent>()
    val eventFlow: SharedFlow<LoginEvent> = _eventFlow.asSharedFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun setDemoAccount(email: String) {
        _uiState.update { it.copy(email = email, password = "123456", errorMessage = null) }
    }

    fun login() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password.trim()

        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Vui lòng nhập đầy đủ email và mật khẩu") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.login(email, password)
            result.onSuccess { user ->
                // Firestore rules require auth, so seed only after sign-in succeeded
                seedRepository.seedIfEmpty()
                seedRepository.seedMissingMenuItems()
                if (user.userRole == UserRole.STUDENT) {
                    seedRepository.grantStartingPointsIfMissing(user.uid)
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                _eventFlow.emit(LoginEvent.LoginSuccess(user))
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Sai email hoặc mật khẩu"
                    )
                }
            }
        }
    }
}

