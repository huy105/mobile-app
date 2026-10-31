package com.example.canteen.ui.canteen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.canteen.data.model.CancelReason
import com.example.canteen.data.model.Order
import com.example.canteen.data.model.OrderStatus
import com.example.canteen.ui.common.components.LoadingBox
import com.example.canteen.ui.common.components.RawTag
import com.example.canteen.ui.common.theme.Background
import com.example.canteen.ui.common.theme.Error
import com.example.canteen.ui.common.theme.ErrorContainer
import com.example.canteen.ui.common.theme.Info
import com.example.canteen.ui.common.theme.InfoContainer
import com.example.canteen.ui.common.theme.OnPrimary
import com.example.canteen.ui.common.theme.OnPrimaryContainer
import com.example.canteen.ui.common.theme.OnSurface
import com.example.canteen.ui.common.theme.OnSurfaceVariant
import com.example.canteen.ui.common.theme.Outline
import com.example.canteen.ui.common.theme.Primary
import com.example.canteen.ui.common.theme.PrimaryContainer
import com.example.canteen.ui.common.theme.OnSecondaryContainer
import com.example.canteen.ui.common.theme.SecondaryContainer
import com.example.canteen.ui.common.theme.ShapeCard
import com.example.canteen.ui.common.theme.ShapePill
import com.example.canteen.ui.common.theme.Success
import com.example.canteen.ui.common.theme.SuccessContainer
import com.example.canteen.ui.common.theme.Surface
import com.example.canteen.ui.common.theme.SurfaceVariant

@Composable
fun CanteenOrdersScreen(
    modifier: Modifier = Modifier,
    viewModel: CanteenOrdersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var orderToCancel by remember { mutableStateOf<Order?>(null) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Background)) {
        if (uiState.isLoading) {
            LoadingBox(message = "Đang tải danh sách đơn hàng...")
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top filter chips for time slots
                SlotFilterChips(
                    slots = uiState.slots,
                    selectedSlotId = uiState.selectedSlotId,
                    slotOrderCounts = uiState.slotOrderCounts,
                    totalActiveOrders = uiState.activeOrders.size,
                    onSelectSlot = { viewModel.selectSlot(it) }
                )

                // Kanban 3 columns
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val isWide = maxWidth >= 760.dp

                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            KanbanColumn(
                                title = "Đã nhận",
                                count = uiState.receivedOrders.size,
                                tagBgColor = InfoContainer,
                                tagTextColor = Info,
                                orders = uiState.receivedOrders,
                                onAdvance = { viewModel.advanceStatus(it) },
                                onCancel = { orderToCancel = it },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )

                            KanbanColumn(
                                title = "Đang chuẩn bị",
                                count = uiState.preparingOrders.size,
                                tagBgColor = SecondaryContainer,
                                tagTextColor = OnSecondaryContainer,
                                orders = uiState.preparingOrders,
                                onAdvance = { viewModel.advanceStatus(it) },
                                onCancel = { orderToCancel = it },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )

                            KanbanColumn(
                                title = "Sẵn sàng lấy",
                                count = uiState.readyOrders.size,
                                tagBgColor = SuccessContainer,
                                tagTextColor = Success,
                                orders = uiState.readyOrders,
                                onAdvance = { viewModel.advanceStatus(it) },
                                onCancel = { orderToCancel = it },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    } else {
                        // Horizontal scrollable columns for smaller screens
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            KanbanColumn(
                                title = "Đã nhận",
                                count = uiState.receivedOrders.size,
                                tagBgColor = InfoContainer,
                                tagTextColor = Info,
                                orders = uiState.receivedOrders,
                                onAdvance = { viewModel.advanceStatus(it) },
                                onCancel = { orderToCancel = it },
                                modifier = Modifier.width(300.dp).fillMaxHeight()
                            )

                            KanbanColumn(
                                title = "Đang chuẩn bị",
                                count = uiState.preparingOrders.size,
                                tagBgColor = SecondaryContainer,
                                tagTextColor = OnSecondaryContainer,
                                orders = uiState.preparingOrders,
                                onAdvance = { viewModel.advanceStatus(it) },
                                onCancel = { orderToCancel = it },
                                modifier = Modifier.width(300.dp).fillMaxHeight()
                            )

                            KanbanColumn(
                                title = "Sẵn sàng lấy",
                                count = uiState.readyOrders.size,
                                tagBgColor = SuccessContainer,
                                tagTextColor = Success,
                                orders = uiState.readyOrders,
                                onAdvance = { viewModel.advanceStatus(it) },
                                onCancel = { orderToCancel = it },
                                modifier = Modifier.width(300.dp).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Cancel Confirmation Dialog with reason selection
    orderToCancel?.let { order ->
        val isReady = order.status == OrderStatus.READY.name
        var selectedReason by remember(order.id) {
            mutableStateOf(if (isReady) CancelReason.NO_SHOW else CancelReason.SOLD_OUT)
        }
        val reasons = listOf(
            CancelReason.SOLD_OUT,
            CancelReason.OVERLOAD,
            CancelReason.NO_SHOW,
            CancelReason.OTHER
        )

        AlertDialog(
            onDismissRequest = { orderToCancel = null },
            title = {
                Text(
                    text = "Hủy đơn #${order.pickupCode}?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Chọn lý do hủy đơn của ${order.userName.ifBlank { "học sinh" }}:",
                        fontSize = 14.sp,
                        color = OnSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    reasons.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReason = reason }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason },
                                colors = RadioButtonDefaults.colors(selectedColor = Primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = reason.label, fontSize = 14.sp, color = OnSurface)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelOrder(order.id, selectedReason)
                        orderToCancel = null
                    }
                ) {
                    Text("Xác nhận hủy", color = Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { orderToCancel = null }) {
                    Text("Giữ đơn")
                }
            }
        )
    }
}

@Composable
private fun SlotFilterChips(
    slots: List<com.example.canteen.data.model.TimeSlot>,
    selectedSlotId: String?,
    slotOrderCounts: Map<String, Int>,
    totalActiveOrders: Int,
    onSelectSlot: (String?) -> Unit
) {
    val lunchSlots = remember(slots) {
        slots.filter { it.startMinute < 840 }.sortedBy { it.startMinute }
    }
    val eveningSlots = remember(slots) {
        slots.filter { it.startMinute >= 840 }.sortedBy { it.startMinute }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            val isSelected = selectedSlotId == null
            val countText = if (totalActiveOrders > 0) " ($totalActiveOrders)" else ""
            FilterChipItem(
                label = "Tất cả$countText",
                isSelected = isSelected,
                onClick = { onSelectSlot(null) }
            )
        }

        if (lunchSlots.isNotEmpty()) {
            item {
                GroupHeaderBadge(title = "☀️ Buổi trưa")
            }

            items(lunchSlots) { slot ->
                val isSelected = selectedSlotId == slot.id
                val orderCount = slotOrderCounts[slot.id] ?: 0
                val countText = if (orderCount > 0) " ($orderCount)" else ""
                FilterChipItem(
                    label = "${slot.label}$countText",
                    isSelected = isSelected,
                    onClick = { onSelectSlot(slot.id) }
                )
            }
        }

        if (eveningSlots.isNotEmpty()) {
            item {
                GroupHeaderBadge(title = "🌙 Chiều tối")
            }

            items(eveningSlots) { slot ->
                val isSelected = selectedSlotId == slot.id
                val orderCount = slotOrderCounts[slot.id] ?: 0
                val countText = if (orderCount > 0) " ($orderCount)" else ""
                FilterChipItem(
                    label = "${slot.label}$countText",
                    isSelected = isSelected,
                    onClick = { onSelectSlot(slot.id) }
                )
            }
        }
    }
}

@Composable
private fun GroupHeaderBadge(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = OnSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, end = 2.dp)
    )
}

@Composable
private fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(ShapePill)
            .background(if (isSelected) PrimaryContainer else Surface)
            .border(
                width = 1.dp,
                color = if (isSelected) Primary else Outline,
                shape = ShapePill
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) OnPrimaryContainer else OnSurfaceVariant
        )
    }
}

@Composable
private fun KanbanColumn(
    title: String,
    count: Int,
    tagBgColor: Color,
    tagTextColor: Color,
    orders: List<Order>,
    onAdvance: (Order) -> Unit,
    onCancel: (Order) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF9F6F0))
            .border(1.dp, Outline, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        // Column header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            RawTag(
                label = count.toString(),
                backgroundColor = tagBgColor,
                textColor = tagTextColor
            )
        }

        if (orders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface)
                    .border(1.dp, Outline, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Không có đơn",
                    fontSize = 13.sp,
                    color = OnSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(orders, key = { it.id }) { order ->
                    CanteenOrderCard(
                        order = order,
                        onAdvance = { onAdvance(order) },
                        onCancel = { onCancel(order) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CanteenOrderCard(
    order: Order,
    onAdvance: () -> Unit,
    onCancel: () -> Unit
) {
    val isReady = order.status == OrderStatus.READY.name
    val borderColor = if (isReady) Success else Outline
    val borderWidth = if (isReady) 1.5.dp else 1.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCard)
            .background(Surface)
            .border(borderWidth, borderColor, ShapeCard)
            .padding(14.dp)
    ) {
        // Header: Pickup code + slot label or status tag
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = order.pickupCode,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                color = OnSurface
            )

            if (isReady) {
                RawTag(
                    label = "READY",
                    backgroundColor = SuccessContainer,
                    textColor = Success
                )
            } else {
                val slotDisplay = if (order.slotLabel.isNotBlank()) {
                    order.slotLabel.substringBefore(" - ").trim()
                } else "Khung giờ"
                RawTag(
                    label = slotDisplay,
                    backgroundColor = SurfaceVariant,
                    textColor = OnSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Customer name
        Text(
            text = order.userName.ifBlank { "Học sinh" },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Items list: 2× Cơm gà xối mỡ, 1× Trà tắc
        val itemsSummary = order.items.joinToString(", ") { "${it.quantity}× ${it.name}" }
        Text(
            text = itemsSummary,
            fontSize = 13.sp,
            color = OnSurfaceVariant,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Action buttons based on status (height >= 36dp per design note)
        when (order.status) {
            OrderStatus.RECEIVED.name -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButton(
                        text = "Bắt đầu làm",
                        backgroundColor = Primary,
                        textColor = OnPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = onAdvance
                    )
                    ActionButton(
                        text = "Hủy",
                        backgroundColor = ErrorContainer,
                        textColor = Error,
                        onClick = onCancel
                    )
                }
            }
            OrderStatus.PREPARING.name -> {
                ActionButton(
                    text = "Sẵn sàng ✓",
                    backgroundColor = Success,
                    textColor = OnPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAdvance
                )
            }
            OrderStatus.READY.name -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButton(
                        text = "Hoàn thành",
                        backgroundColor = Surface,
                        textColor = OnSurface,
                        border = 1.dp to Outline,
                        modifier = Modifier.weight(1f),
                        onClick = onAdvance
                    )
                    ActionButton(
                        text = "Không đến lấy",
                        backgroundColor = ErrorContainer,
                        textColor = Error,
                        onClick = onCancel
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    border: Pair<androidx.compose.ui.unit.Dp, Color>? = null,
    onClick: () -> Unit
) {
    val borderModifier = if (border != null) {
        Modifier.border(border.first, border.second, RoundedCornerShape(10.dp))
    } else Modifier

    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .then(borderModifier)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

