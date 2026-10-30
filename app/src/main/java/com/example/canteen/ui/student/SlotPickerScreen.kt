package com.example.canteen.ui.student

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canteen.data.model.TimeSlot
import com.example.canteen.ui.common.components.LoadingBox
import com.example.canteen.ui.common.components.PrimaryButton
import com.example.canteen.ui.common.components.RawTag
import com.example.canteen.ui.common.theme.Background
import com.example.canteen.ui.common.theme.Error
import com.example.canteen.ui.common.theme.ErrorContainer
import com.example.canteen.ui.common.theme.OnSurface
import com.example.canteen.ui.common.theme.OnSurfaceVariant
import com.example.canteen.ui.common.theme.Outline
import com.example.canteen.ui.common.theme.Primary
import com.example.canteen.ui.common.theme.PrimaryContainer
import com.example.canteen.ui.common.theme.SecondaryContainer
import com.example.canteen.ui.common.theme.ShapeCard
import com.example.canteen.ui.common.theme.Surface
import com.example.canteen.ui.common.theme.SurfaceVariant
import com.example.canteen.ui.common.util.formatVnd

@Composable
fun SlotPickerScreen(
    viewModel: SlotPickerViewModel,
    onNavigateBack: () -> Unit,
    onOrderCreated: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val cartState by viewModel.cartState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.createdOrderId) {
        uiState.createdOrderId?.let { orderId ->
            onOrderCreated(orderId)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Background)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onNavigateBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "←", fontSize = 22.sp, color = OnSurface)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Chọn giờ nhận",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .border(width = 1.dp, color = Outline)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cartState.totalPrice.formatVnd(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = OnSurface
                    )
                    Text(
                        text = uiState.selectedSlot?.label ?: "Chưa chọn khung giờ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                PrimaryButton(
                    text = "Xác nhận đặt",
                    onClick = { viewModel.confirmOrder() },
                    enabled = uiState.selectedSlotId != null && !uiState.isSubmitting,
                    isLoading = uiState.isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        containerColor = Background
    ) { innerPadding ->
        if (uiState.isLoading) {
            LoadingBox(message = "Đang tải khung giờ...")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items = uiState.slots, key = { it.id }) { slot ->
                    val isPast = viewModel.isSlotPast(slot)
                    val isFull = slot.isFull
                    val isSelected = uiState.selectedSlotId == slot.id

                    SlotItemCard(
                        slot = slot,
                        isSelected = isSelected,
                        isPast = isPast,
                        isFull = isFull,
                        onClick = { viewModel.selectSlot(slot.id) }
                    )
                }

                // Demo payment disclaimer
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeCard)
                            .background(SecondaryContainer)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "💡 Thanh toán giả lập cho bản demo: trả tại quầy khi nhận đồ.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = OnSurface,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SlotItemCard(
    slot: TimeSlot,
    isSelected: Boolean,
    isPast: Boolean,
    isFull: Boolean,
    onClick: () -> Unit
) {
    val isDisabled = isPast || isFull
    val alpha = if (isDisabled) 0.5f else 1f

    val bg = when {
        isSelected -> PrimaryContainer
        isDisabled -> SurfaceVariant
        else -> Surface
    }

    val borderColor = if (isSelected) Primary else Outline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCard)
            .background(bg)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, ShapeCard)
            .clickable(enabled = !isDisabled) { onClick() }
            .padding(14.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = slot.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            val subtitle = when {
                isPast -> "Đã qua giờ"
                isFull -> "Đã đầy"
                else -> "Còn ${slot.remainingOrders} suất"
            }
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = OnSurfaceVariant
            )
        }

        when {
            isPast -> {
                RawTag(
                    label = "Đã qua",
                    backgroundColor = SurfaceVariant,
                    textColor = OnSurfaceVariant
                )
            }
            isFull -> {
                RawTag(
                    label = "Hết suất",
                    backgroundColor = ErrorContainer,
                    textColor = Error
                )
            }
            else -> {
                // Radio indicator
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Surface)
                        .border(
                            width = if (isSelected) 7.dp else 2.dp,
                            color = if (isSelected) Primary else Outline,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

