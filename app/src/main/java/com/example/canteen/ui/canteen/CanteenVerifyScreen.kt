package com.example.canteen.ui.canteen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.canteen.ui.common.theme.Background
import com.example.canteen.ui.common.theme.Error
import com.example.canteen.ui.common.theme.ErrorContainer
import com.example.canteen.ui.common.theme.OnPrimary
import com.example.canteen.ui.common.theme.OnSurface
import com.example.canteen.ui.common.theme.OnSurfaceVariant
import com.example.canteen.ui.common.theme.Outline
import com.example.canteen.ui.common.theme.Primary
import com.example.canteen.ui.common.theme.Success
import com.example.canteen.ui.common.theme.SuccessContainer
import com.example.canteen.ui.common.theme.Surface

@Composable
fun CanteenVerifyScreen(
    modifier: Modifier = Modifier,
    viewModel: CanteenVerifyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Trả đồ",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = OnSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Nhập mã 4 số trên điện thoại học sinh",
            fontSize = 14.sp,
            color = OnSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Success banner
        AnimatedVisibility(
            visible = uiState.successMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            uiState.successMessage?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SuccessContainer)
                        .border(1.dp, Success, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "✅", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = message,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Success,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Error banner
        AnimatedVisibility(
            visible = uiState.errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            uiState.errorMessage?.let { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ErrorContainer)
                        .border(1.dp, Error, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⚠️", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = error,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Error,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4 PIN Boxes
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 4) {
                val digit = uiState.code.getOrNull(i)?.toString() ?: ""
                val isFocused = uiState.code.length == i

                val borderColor = when {
                    uiState.isError -> Error
                    isFocused -> Primary
                    digit.isNotEmpty() -> Primary
                    else -> Outline
                }
                val borderWidth = if (uiState.isError || isFocused || digit.isNotEmpty()) 2.dp else 1.dp

                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 68.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface)
                        .border(borderWidth, borderColor, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = digit,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (uiState.isError) Error else OnSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.isVerifying) {
            Box(
                modifier = Modifier
                    .height(240.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Primary, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Đang kiểm tra mã...",
                        fontSize = 14.sp,
                        color = OnSurfaceVariant
                    )
                }
            }
        } else {
            // Keypad (4 rows × 3 columns, height 56dp per key)
            CanteenKeypad(
                onDigit = { viewModel.onDigit(it) },
                onBackspace = { viewModel.onBackspace() },
                onConfirm = { viewModel.verifyCode() }
            )
        }
    }
}

@Composable
private fun CanteenKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(320.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: 1, 2, 3
        KeypadRow(
            keys = listOf("1", "2", "3"),
            onKeyClick = { onDigit(it.first()) }
        )

        // Row 2: 4, 5, 6
        KeypadRow(
            keys = listOf("4", "5", "6"),
            onKeyClick = { onDigit(it.first()) }
        )

        // Row 3: 7, 8, 9
        KeypadRow(
            keys = listOf("7", "8", "9"),
            onKeyClick = { onDigit(it.first()) }
        )

        // Row 4: ⌫, 0, ✓
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Backspace key
            KeypadButton(
                text = "⌫",
                textColor = Error,
                backgroundColor = Surface,
                borderColor = Outline,
                modifier = Modifier.weight(1f),
                onClick = onBackspace
            )

            // Digit 0
            KeypadButton(
                text = "0",
                textColor = OnSurface,
                backgroundColor = Surface,
                borderColor = Outline,
                modifier = Modifier.weight(1f),
                onClick = { onDigit('0') }
            )

            // Confirm / Checkmark key
            KeypadButton(
                text = "✓",
                textColor = OnPrimary,
                backgroundColor = Primary,
                borderColor = Primary,
                modifier = Modifier.weight(1f),
                onClick = onConfirm
            )
        }
    }
}

@Composable
private fun KeypadRow(
    keys: List<String>,
    onKeyClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (key in keys) {
            KeypadButton(
                text = key,
                textColor = OnSurface,
                backgroundColor = Surface,
                borderColor = Outline,
                modifier = Modifier.weight(1f),
                onClick = { onKeyClick(key) }
            )
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    textColor: Color,
    backgroundColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

