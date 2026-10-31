package com.example.canteen.ui.canteen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.canteen.ui.common.theme.Surface
import com.example.canteen.ui.common.theme.SurfaceVariant

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToOrders: () -> Unit = {},
    onNavigateToMenu: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        LoadingBox(modifier = modifier.fillMaxSize())
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTabletLandscape = maxWidth >= 600.dp

        if (isTabletLandscape) {
            DashboardTabletLayout(
                uiState = uiState,
                onNavigateToOrders = onNavigateToOrders,
                onNavigateToMenu = onNavigateToMenu
            )
        } else {
            DashboardPhoneLayout(
                uiState = uiState,
                onNavigateToOrders = onNavigateToOrders,
                onNavigateToMenu = onNavigateToMenu
            )
        }
    }
}

@Composable
private fun DashboardTabletLayout(
    uiState: DashboardUiState,
    onNavigateToOrders: () -> Unit,
    onNavigateToMenu: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        DashboardHeader(dateText = uiState.formattedDate)

        // 4 Stat Cards in a row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = stringResource(R.string.dashboard_revenue),
                value = uiState.revenueText,
                subtitle = uiState.revenueSubtitle,
                isHighlighted = true,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(R.string.dashboard_total_orders),
                value = uiState.totalOrdersCount.toString(),
                subtitle = uiState.ordersSubtitle,
                onClick = onNavigateToOrders,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(R.string.dashboard_items_sold),
                value = uiState.itemsSoldCount.toString(),
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
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
            )
            LowStockDishesCard(
                items = uiState.lowStockItems,
                onClick = onNavigateToMenu,
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
    onNavigateToOrders: () -> Unit,
    onNavigateToMenu: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        DashboardHeader(dateText = uiState.formattedDate)

        // 2x2 Grid for Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = stringResource(R.string.dashboard_revenue),
                value = uiState.revenueText,
                subtitle = uiState.revenueSubtitle,
                isHighlighted = true,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(R.string.dashboard_total_orders),
                value = uiState.totalOrdersCount.toString(),
                subtitle = uiState.ordersSubtitle,
                onClick = onNavigateToOrders,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = stringResource(R.string.dashboard_items_sold),
                value = uiState.itemsSoldCount.toString(),
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
            items = uiState.lowStockItems,
            onClick = onNavigateToMenu,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DashboardHeader(dateText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = dateText,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = OnSurface
        )
        Text(
            text = stringResource(R.string.dashboard_realtime_subtitle),
            fontSize = 12.sp,
            color = OnSurfaceVariant
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
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
                text = stringResource(R.string.dashboard_slots_title).uppercase(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

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
                for (slot in slots) {
                    SlotRow(slot = slot)
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
            .padding(vertical = 7.dp),
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
    items: List<DashboardLowStockItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
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
                text = stringResource(R.string.dashboard_low_stock_title).uppercase(),
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

