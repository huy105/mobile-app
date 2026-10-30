package com.example.canteen.ui.student

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canteen.data.model.OrderStatus
import com.example.canteen.ui.common.components.ButtonStyle
import com.example.canteen.ui.common.components.EmptyState
import com.example.canteen.ui.common.components.LoadingBox
import com.example.canteen.ui.common.components.PrimaryButton
import com.example.canteen.ui.common.components.StatusTag
import com.example.canteen.ui.common.theme.Background
import com.example.canteen.ui.common.theme.Error
import com.example.canteen.ui.common.theme.OnPrimary
import com.example.canteen.ui.common.theme.OnSurface
import com.example.canteen.ui.common.theme.OnSurfaceVariant
import com.example.canteen.ui.common.theme.Outline
import com.example.canteen.ui.common.theme.PickupCodeStyle
import com.example.canteen.ui.common.theme.Primary
import com.example.canteen.ui.common.theme.PrimaryContainer
import com.example.canteen.ui.common.theme.ShapeCard
import com.example.canteen.ui.common.theme.ShapeCodeBanner
import com.example.canteen.ui.common.theme.Success
import com.example.canteen.ui.common.theme.Surface
import com.example.canteen.ui.common.theme.SurfaceVariant
import com.example.canteen.ui.common.util.formatVnd

@Composable
fun OrderStatusScreen(
    orderId: String,
    viewModel: OrderStatusViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCancelDialog by remember { mutableStateOf(false) }

    LaunchedEffect(orderId) {
        viewModel.setOrderId(orderId)
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
                    text = "Đơn của bạn",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            }
        },
        containerColor = Background
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                LoadingBox(message = "Đang tải thông tin đơn hàng...")
            }
            uiState.order == null -> {
                EmptyState(
                    icon = "🧾",
                    title = "Không tìm thấy đơn hàng",
                    description = "Đơn hàng không tồn tại hoặc đã bị xóa."
                )
            }
            else -> {
                val order = uiState.order!!
                val isReady = order.orderStatus == OrderStatus.READY

                val bannerBg by animateColorAsState(
                    targetValue = if (isReady) Success else Primary,
                    animationSpec = tween(300),
                    label = "codeBannerBg"
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Big Pickup Code Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeCodeBanner)
                            .background(bannerBg)
                            .padding(vertical = 20.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isReady) "Đến quầy nhận đồ ngay!" else "Mã nhận đồ",
                            color = OnPrimary.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = order.pickupCode,
                            style = PickupCodeStyle,
                            color = OnPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Khung giờ ${order.slotLabel}",
                            color = OnPrimary.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Realtime Stepper or Status Tag
                    if (order.orderStatus == OrderStatus.CANCELLED) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ShapeCard)
                                .background(Surface)
                                .border(1.dp, Outline, ShapeCard)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            StatusTag(status = order.status)
                        }
                    } else {
                        OrderStepper(currentStatus = order.orderStatus)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Order Details Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeCard)
                            .background(Surface)
                            .border(1.dp, Outline, ShapeCard)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "CHI TIẾT ĐƠN",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        order.items.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${item.quantity} × ${item.name}",
                                    fontSize = 14.sp,
                                    color = OnSurface
                                )
                                Text(
                                    text = item.subtotal.formatVnd(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Outline, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tổng",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = OnSurface
                            )
                            Text(
                                text = order.total.formatVnd(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Primary
                            )
                        }
                    }

                    // Cancel Order button (ONLY visible when RECEIVED)
                    if (order.orderStatus == OrderStatus.RECEIVED) {
                        Spacer(modifier = Modifier.height(16.dp))
                        PrimaryButton(
                            text = "Hủy đơn",
                            onClick = { showCancelDialog = true },
                            style = ButtonStyle.DANGER,
                            isLoading = uiState.isCancelling,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(text = "Hủy đơn hàng", fontWeight = FontWeight.Bold) },
            text = { Text(text = "Bạn có chắc chắn muốn hủy đơn hàng này không? Suất khung giờ sẽ được hoàn lại.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        viewModel.cancelOrder()
                    }
                ) {
                    Text(text = "Hủy đơn", color = Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(text = "Đóng", color = OnSurface)
                }
            }
        )
    }
}

@Composable
fun OrderStepper(
    currentStatus: OrderStatus,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        Pair(OrderStatus.RECEIVED, "Đã nhận"),
        Pair(OrderStatus.PREPARING, "Đang chuẩn bị"),
        Pair(OrderStatus.READY, "Sẵn sàng"),
        Pair(OrderStatus.COMPLETED, "Hoàn thành")
    )

    val currentIndex = when (currentStatus) {
        OrderStatus.RECEIVED -> 0
        OrderStatus.PREPARING -> 1
        OrderStatus.READY -> 2
        OrderStatus.COMPLETED -> 3
        OrderStatus.CANCELLED -> -1
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        steps.forEachIndexed { index, (_, label) ->
            val isDone = index < currentIndex
            val isCurrent = index == currentIndex

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Circle Indicator
                val circleBg = when {
                    isDone -> Success
                    isCurrent -> Primary
                    else -> Outline
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(circleBg)
                        .then(
                            if (isCurrent) Modifier.border(4.dp, PrimaryContainer, CircleShape) else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val iconText = when {
                        isDone -> "✓"
                        isCurrent -> "●"
                        else -> (index + 1).toString()
                    }
                    Text(
                        text = iconText,
                        color = if (isDone || isCurrent) OnPrimary else OnSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrent) Primary else if (isDone) OnSurface else OnSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
