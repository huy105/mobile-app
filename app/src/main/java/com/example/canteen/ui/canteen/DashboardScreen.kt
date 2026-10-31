package com.example.canteen.ui.canteen

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.canteen.R
import com.example.canteen.ui.common.components.CommonDatePickerDialog
import com.example.canteen.ui.common.components.DateBar
import com.example.canteen.ui.common.components.LoadingBox
import com.example.canteen.ui.common.components.RawTag
import com.example.canteen.ui.common.theme.Error
import com.example.canteen.ui.common.theme.ErrorContainer
import com.example.canteen.ui.common.theme.OnSecondaryContainer
import com.example.canteen.ui.common.theme.OnSurface
import com.example.canteen.ui.common.theme.OnSurfaceVariant
import com.example.canteen.ui.common.theme.Outline
import com.example.canteen.ui.common.theme.Primary
import com.example.canteen.ui.common.theme.SecondaryContainer
import com.example.canteen.ui.common.theme.Success
import com.example.canteen.ui.common.theme.SuccessContainer
import com.example.canteen.ui.common.util.formatVnd
import com.example.canteen.ui.common.theme.Surface
import com.example.canteen.ui.common.theme.SurfaceVariant
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToOrders: () -> Unit = {},
    onNavigateToMenu: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        CommonDatePickerDialog(
            selectedDate = uiState.selectedDate,
            onDateSelected = { viewModel.selectDate(it) },
            onDismiss = { showDatePicker = false }
        )
    }

    if (uiState.isLoading) {
        LoadingBox(modifier = modifier.fillMaxSize())
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTabletLandscape = maxWidth >= 600.dp

        if (isTabletLandscape) {
            DashboardTabletLayout(
                uiState = uiState,
                onPreviousDay = { viewModel.previousDay() },
                onNextDay = { viewModel.nextDay() },
                onResetToToday = { viewModel.resetToToday() },
                onOpenDatePicker = { showDatePicker = true },
                onNavigateToOrders = onNavigateToOrders,
                onNavigateToMenu = onNavigateToMenu
            )
        } else {
            DashboardPhoneLayout(
                uiState = uiState,
                onPreviousDay = { viewModel.previousDay() },
                onNextDay = { viewModel.nextDay() },
                onResetToToday = { viewModel.resetToToday() },
                onOpenDatePicker = { showDatePicker = true },
                onNavigateToOrders = onNavigateToOrders,
                onNavigateToMenu = onNavigateToMenu
            )
        }
    }
}

@Composable
private fun DashboardTabletLayout(
    uiState: DashboardUiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onResetToToday: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToMenu: () -> Unit
) {
    val animRevenue by animateIntAsState(targetValue = uiState.revenue.toInt(), animationSpec = tween(400), label = "dashRevTab")
    val animTotalOrders by animateIntAsState(targetValue = uiState.totalOrdersCount, animationSpec = tween(400), label = "dashOrdersTab")
    val animItemsSold by animateIntAsState(targetValue = uiState.itemsSoldCount, animationSpec = tween(400), label = "dashItemsTab")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Date navigation bar
        DateBar(
            selectedDate = uiState.selectedDate,
            isTablet = true,
            onPreviousDay = onPreviousDay,
            onNextDay = onNextDay,
            onResetToToday = onResetToToday,
            onOpenDatePicker = onOpenDatePicker,
            isToday = uiState.isToday,
            formattedDate = uiState.formattedDate,
            hasOrders = uiState.hasOrders,
            emptyOrdersMessage = stringResource(R.string.dashboard_no_orders_day),
            rightContent = if (uiState.isToday) {
                {
                    Text(
                        text = stringResource(R.string.dashboard_realtime_subtitle),
                        fontSize = 12.sp,
                        color = OnSurfaceVariant
                    )
                }
            } else null
        )

        // 4 Stat Cards in a row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = stringResource(R.string.dashboard_revenue),
                value = animRevenue.toLong().formatVnd(),
                subtitle = uiState.revenueSubtitle,
                isHighlighted = true,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(R.string.dashboard_total_orders),
                value = animTotalOrders.toString(),
                subtitle = uiState.ordersSubtitle,
                onClick = if (uiState.isToday) onNavigateToOrders else null,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(R.string.dashboard_items_sold),
                value = animItemsSold.toString(),
                subtitle = uiState.itemsSoldSubtitle,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(R.string.dashboard_avg_prep_time),
                value = uiState.avgPrepTimeText,
                subtitle = uiState.avgPrepSubtitle,
                modifier = Modifier.weight(1f)
            )
        }

        // Two cards side by side
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TimeSlotsCard(
                slots = uiState.slots,
                scrollable = true,
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
            )
            LowStockDishesCard(
                title = if (uiState.isToday) {
                    stringResource(R.string.dashboard_low_stock_title)
                } else {
                    stringResource(R.string.dashboard_low_stock_title_past)
                },
                items = uiState.lowStockItems,
                onClick = if (uiState.isToday) onNavigateToMenu else null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun DashboardPhoneLayout(
    uiState: DashboardUiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onResetToToday: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToMenu: () -> Unit
) {
    val animRevenue by animateIntAsState(targetValue = uiState.revenue.toInt(), animationSpec = tween(400), label = "dashRevPhone")
    val animTotalOrders by animateIntAsState(targetValue = uiState.totalOrdersCount, animationSpec = tween(400), label = "dashOrdersPhone")
    val animItemsSold by animateIntAsState(targetValue = uiState.itemsSoldCount, animationSpec = tween(400), label = "dashItemsPhone")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Date navigation bar
        DateBar(
            selectedDate = uiState.selectedDate,
            isTablet = false,
            onPreviousDay = onPreviousDay,
            onNextDay = onNextDay,
            onResetToToday = onResetToToday,
            onOpenDatePicker = onOpenDatePicker,
            isToday = uiState.isToday,
            formattedDate = uiState.formattedDate,
            hasOrders = uiState.hasOrders,
            emptyOrdersMessage = stringResource(R.string.dashboard_no_orders_day)
        )

        // 2x2 Grid for Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = stringResource(R.string.dashboard_revenue),
                value = animRevenue.toLong().formatVnd(),
                subtitle = uiState.revenueSubtitle,
                isHighlighted = true,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(R.string.dashboard_total_orders),
                value = animTotalOrders.toString(),
                subtitle = uiState.ordersSubtitle,
                onClick = if (uiState.isToday) onNavigateToOrders else null,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = stringResource(R.string.dashboard_items_sold),
                value = animItemsSold.toString(),
                subtitle = uiState.itemsSoldSubtitle,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(R.string.dashboard_avg_prep_time),
                value = uiState.avgPrepTimeText,
                subtitle = uiState.avgPrepSubtitle,
                modifier = Modifier.weight(1f)
            )
        }

        // Time slots card
        TimeSlotsCard(
            slots = uiState.slots,
            modifier = Modifier.fillMaxWidth()
        )

        // Low stock dishes card
        LowStockDishesCard(
            title = if (uiState.isToday) {
                stringResource(R.string.dashboard_low_stock_title)
            } else {
                stringResource(R.string.dashboard_low_stock_title_past)
            },
            items = uiState.lowStockItems,
            onClick = if (uiState.isToday) onNavigateToMenu else null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) {
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    } else {
        modifier
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) Primary else Surface
        ),
        border = if (isHighlighted) null else BorderStroke(1.dp, Outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isHighlighted) Color.White.copy(alpha = 0.9f) else OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color = if (isHighlighted) Color.White else OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (isHighlighted) Color.White.copy(alpha = 0.9f) else OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TimeSlotsCard(
    slots: List<DashboardSlotItem>,
    modifier: Modifier = Modifier,
    // Chỉ cuộn trong thẻ khi thẻ có chiều cao cố định (tablet). Trên điện thoại cả trang đã
    // cuộn dọc: cuộn lồng nhau sẽ crash (infinite maximum height).
    scrollable: Boolean = false
) {
    val lunchSlots = remember(slots) {
        slots.filter { it.startMinute < 840 }.sortedBy { it.startMinute }
    }
    val eveningSlots = remember(slots) {
        slots.filter { it.startMinute >= 840 }.sortedBy { it.startMinute }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = stringResource(R.string.dashboard_slots_title).uppercase(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant,
                letterSpacing = 0.5.sp
            )

            if (slots.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Chưa có khung giờ nào",
                        fontSize = 13.sp,
                        color = OnSurfaceVariant
                    )
                }
            } else {
                if (lunchSlots.isNotEmpty()) {
                    Text(
                        text = "☀️ Buổi trưa",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    for (slot in lunchSlots) {
                        SlotRow(slot = slot)
                    }
                }

                if (eveningSlots.isNotEmpty()) {
                    Text(
                        text = "🌙 Chiều tối",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                    for (slot in eveningSlots) {
                        SlotRow(slot = slot)
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotRow(slot: DashboardSlotItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = slot.label,
            modifier = Modifier.width(96.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface
        )

        // Capacity Progress bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(SurfaceVariant)
        ) {
            val barColor = when (slot.status) {
                SlotCapacityStatus.FULL -> Error
                SlotCapacityStatus.ALMOST_FULL -> Color(0xFFEA580C)
                SlotCapacityStatus.AVAILABLE -> Primary
            }
            if (slot.progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = slot.progress)
                        .clip(RoundedCornerShape(5.dp))
                        .background(barColor)
                )
            }
        }

        // Count (e.g. 10/10)
        Text(
            text = "${slot.count}/${slot.maxOrders}",
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurfaceVariant
        )

        // Tag: Đầy / Gần đầy / Còn chỗ
        when (slot.status) {
            SlotCapacityStatus.FULL -> RawTag(
                label = stringResource(R.string.slot_tag_full),
                backgroundColor = ErrorContainer,
                textColor = Error
            )
            SlotCapacityStatus.ALMOST_FULL -> RawTag(
                label = stringResource(R.string.slot_tag_almost_full),
                backgroundColor = SecondaryContainer,
                textColor = OnSecondaryContainer
            )
            SlotCapacityStatus.AVAILABLE -> RawTag(
                label = stringResource(R.string.slot_tag_available),
                backgroundColor = SuccessContainer,
                textColor = Success
            )
        }
    }
}

@Composable
private fun LowStockDishesCard(
    title: String,
    items: List<DashboardLowStockItem>,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val cardModifier = if (onClick != null) {
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    } else {
        modifier
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title.uppercase(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_low_stock_empty),
                        fontSize = 13.sp,
                        color = OnSurfaceVariant
                    )
                }
            } else {
                for (item in items) {
                    LowStockDishRow(item = item)
                }
            }
        }
    }
}

@Composable
private fun LowStockDishRow(item: DashboardLowStockItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = item.name,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = if (item.isDimmed) FontWeight.Medium else FontWeight.SemiBold,
            color = if (item.isDimmed) OnSurfaceVariant else OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        when (val tag = item.tagType) {
            is LowStockTagType.SoldOutQuota -> RawTag(
                label = stringResource(R.string.dish_tag_sold_out_quota, tag.sold, tag.total),
                backgroundColor = ErrorContainer,
                textColor = Error
            )
            is LowStockTagType.RemainingQuota -> RawTag(
                label = stringResource(R.string.dish_tag_remaining_quota, tag.remaining),
                backgroundColor = SecondaryContainer,
                textColor = OnSecondaryContainer
            )
            is LowStockTagType.Disabled -> RawTag(
                label = stringResource(R.string.dish_tag_disabled),
                backgroundColor = SurfaceVariant,
                textColor = OnSurfaceVariant
            )
        }
    }
}
