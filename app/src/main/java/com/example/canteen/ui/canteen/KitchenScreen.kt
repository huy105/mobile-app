package com.example.canteen.ui.canteen

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.canteen.ui.common.components.CommonDatePickerDialog
import com.example.canteen.ui.common.components.DateBar
import com.example.canteen.ui.common.components.EmptyState
import com.example.canteen.ui.common.components.LoadingBox
import com.example.canteen.ui.common.components.RawTag
import com.example.canteen.ui.common.theme.Background
import com.example.canteen.ui.common.theme.OnPrimary
import com.example.canteen.ui.common.theme.OnSecondaryContainer
import com.example.canteen.ui.common.theme.OnSurface
import com.example.canteen.ui.common.theme.OnSurfaceVariant
import com.example.canteen.ui.common.theme.Outline
import com.example.canteen.ui.common.theme.Primary
import com.example.canteen.ui.common.theme.SecondaryContainer
import com.example.canteen.ui.common.theme.ShapeCard
import com.example.canteen.ui.common.theme.Surface
import com.example.canteen.ui.common.util.formatVnd

@Composable
fun KitchenScreen(
    viewModel: KitchenViewModel = hiltViewModel(),
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        val isTablet = maxWidth >= 600.dp

        if (isTablet) {
            KitchenTabletLayout(
                uiState = uiState,
                onPreviousDay = { viewModel.previousDay() },
                onNextDay = { viewModel.nextDay() },
                onResetToToday = { viewModel.resetToToday() },
                onOpenDatePicker = { showDatePicker = true }
            )
        } else {
            KitchenPhoneLayout(
                uiState = uiState,
                onPreviousDay = { viewModel.previousDay() },
                onNextDay = { viewModel.nextDay() },
                onResetToToday = { viewModel.resetToToday() },
                onOpenDatePicker = { showDatePicker = true }
            )
        }
    }
}

@Composable
private fun KitchenPhoneLayout(
    uiState: KitchenUiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onResetToToday: () -> Unit,
    onOpenDatePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = uiState.summary

    val animNetRevenue by animateIntAsState(targetValue = summary.netRevenue.toInt(), animationSpec = tween(400), label = "netRev")
    val animCost by animateIntAsState(targetValue = summary.totalCost.toInt(), animationSpec = tween(400), label = "cost")
    val animProfit by animateIntAsState(targetValue = summary.grossProfit.toInt(), animationSpec = tween(400), label = "profit")
    val animDiscount by animateIntAsState(targetValue = summary.totalDiscount.toInt(), animationSpec = tween(400), label = "discount")

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // DateBar
        DateBar(
            selectedDate = uiState.selectedDate,
            isTablet = false,
            onPreviousDay = onPreviousDay,
            onNextDay = onNextDay,
            onResetToToday = onResetToToday,
            onOpenDatePicker = onOpenDatePicker,
            hasOrders = uiState.hasOrders,
            emptyOrdersMessage = if (!uiState.hasOrders) "Chưa có đơn nào trong ngày này" else null
        )

        // Missing cost price warning
        if (summary.missingCostItemsCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SecondaryContainer)
                    .border(1.dp, Outline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "⚠️ ${summary.missingCostItemsCount} món chưa có giá vốn, chi phí có thể thấp hơn thực tế",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSecondaryContainer
                )
            }
        }

        // 4 Stat Cards: 2x2 grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KitchenStatCard(
                title = "Doanh thu thuần",
                value = animNetRevenue.toLong().formatVnd(),
                subtitle = "tính cả đơn chưa giao",
                isHighlighted = true,
                modifier = Modifier.weight(1f)
            )
            KitchenStatCard(
                title = "Chi phí",
                value = animCost.toLong().formatVnd(),
                subtitle = "giá vốn × số suất",
                isHighlighted = false,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KitchenStatCard(
                title = "Lãi gộp",
                value = animProfit.toLong().formatVnd(),
                subtitle = "doanh thu thuần - chi phí",
                isHighlighted = false,
                modifier = Modifier.weight(1f)
            )
            KitchenStatCard(
                title = "Giảm giá đã cho",
                value = animDiscount.toLong().formatVnd(),
                subtitle = "voucher + điểm",
                isHighlighted = false,
                modifier = Modifier.weight(1f)
            )
        }

        // Table "Cần làm"
        KitchenDishTable(
            summary = summary,
            isToday = uiState.isToday,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun KitchenTabletLayout(
    uiState: KitchenUiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onResetToToday: () -> Unit,
    onOpenDatePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = uiState.summary

    val animNetRevenue by animateIntAsState(targetValue = summary.netRevenue.toInt(), animationSpec = tween(400), label = "netRevTab")
    val animCost by animateIntAsState(targetValue = summary.totalCost.toInt(), animationSpec = tween(400), label = "costTab")
    val animProfit by animateIntAsState(targetValue = summary.grossProfit.toInt(), animationSpec = tween(400), label = "profitTab")
    val animDiscount by animateIntAsState(targetValue = summary.totalDiscount.toInt(), animationSpec = tween(400), label = "discountTab")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // DateBar with note on right
        DateBar(
            selectedDate = uiState.selectedDate,
            isTablet = true,
            onPreviousDay = onPreviousDay,
            onNextDay = onNextDay,
            onResetToToday = onResetToToday,
            onOpenDatePicker = onOpenDatePicker,
            hasOrders = uiState.hasOrders,
            emptyOrdersMessage = if (!uiState.hasOrders) "Chưa có đơn nào trong ngày này" else null,
            rightContent = {
                Text(
                    text = "Tính từ đơn không bị hủy",
                    fontSize = 12.sp,
                    color = OnSurfaceVariant
                )
            }
        )

        // Missing cost price warning
        if (summary.missingCostItemsCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SecondaryContainer)
                    .border(1.dp, Outline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "⚠️ ${summary.missingCostItemsCount} món chưa có giá vốn, chi phí có thể thấp hơn thực tế",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSecondaryContainer
                )
            }
        }

        // 4 Stat Cards in a row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KitchenStatCard(
                title = "Doanh thu thuần",
                value = animNetRevenue.toLong().formatVnd(),
                subtitle = "tính cả đơn chưa giao",
                isHighlighted = true,
                modifier = Modifier.weight(1f)
            )
            KitchenStatCard(
                title = "Chi phí",
                value = animCost.toLong().formatVnd(),
                subtitle = "giá vốn × số suất",
                isHighlighted = false,
                modifier = Modifier.weight(1f)
            )
            KitchenStatCard(
                title = "Lãi gộp",
                value = animProfit.toLong().formatVnd(),
                subtitle = "doanh thu thuần - chi phí",
                isHighlighted = false,
                modifier = Modifier.weight(1f)
            )
            KitchenStatCard(
                title = "Giảm giá đã cho",
                value = animDiscount.toLong().formatVnd(),
                subtitle = "voucher + điểm",
                isHighlighted = false,
                modifier = Modifier.weight(1f)
            )
        }

        // Table "Cần làm" taking remaining height
        KitchenDishTable(
            summary = summary,
            isToday = uiState.isToday,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            isScrollable = true
        )
    }
}

@Composable
private fun KitchenStatCard(
    title: String,
    value: String,
    subtitle: String,
    isHighlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val bg = if (isHighlighted) Primary else Surface
    val titleColor = if (isHighlighted) OnPrimary.copy(alpha = 0.9f) else OnSurfaceVariant
    val valueColor = if (isHighlighted) OnPrimary else OnSurface
    val subColor = if (isHighlighted) OnPrimary.copy(alpha = 0.85f) else OnSurfaceVariant

    Card(
        modifier = modifier
            .clip(ShapeCard)
            .border(1.dp, if (isHighlighted) Primary else Outline, ShapeCard),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = ShapeCard
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.padding(vertical = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = subColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun KitchenDishTable(
    summary: KitchenSummary,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    isScrollable: Boolean = false
) {
    Card(
        modifier = modifier
            .clip(ShapeCard)
            .border(1.dp, Outline, ShapeCard),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = ShapeCard
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = if (isToday) "Cần làm hôm nay" else "Cần làm trong ngày",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            if (summary.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Chưa có đơn nào trong ngày này",
                        fontSize = 14.sp,
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // Table header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MÓN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.weight(2.2f)
                    )
                    Text(
                        text = "SỐ SUẤT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        textAlign = TextAlign.End,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.weight(0.8f)
                    )
                    Text(
                        text = "GIÁ VỐN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        textAlign = TextAlign.End,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "DOANH THU",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        textAlign = TextAlign.End,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.weight(1.1f)
                    )
                }

                HorizontalDivider(color = Outline, thickness = 1.dp)

                val itemsColumnModifier = if (isScrollable) {
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                } else {
                    Modifier
                }

                Column(modifier = itemsColumnModifier) {
                    summary.items.forEach { dish ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(2.2f)) {
                                Text(
                                    text = dish.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!dish.hasCostPrice) {
                                    Text(
                                        text = "chưa có giá vốn",
                                        fontSize = 10.sp,
                                        color = OnSecondaryContainer
                                    )
                                }
                            }
                            Text(
                                text = dish.portions.toString(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                color = OnSurface,
                                modifier = Modifier.weight(0.8f)
                            )
                            Text(
                                text = dish.cost.formatVnd(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.End,
                                color = OnSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = dish.revenue.formatVnd(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.End,
                                color = OnSurface,
                                modifier = Modifier.weight(1.1f)
                            )
                        }
                        HorizontalDivider(color = Outline.copy(alpha = 0.6f), thickness = 0.5.dp)
                    }
                }

                HorizontalDivider(color = Outline, thickness = 2.dp)

                // Total Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tổng",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = OnSurface,
                        modifier = Modifier.weight(2.2f)
                    )
                    Text(
                        text = summary.totalPortions.toString(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.End,
                        color = OnSurface,
                        modifier = Modifier.weight(0.8f)
                    )
                    Text(
                        text = summary.totalCost.formatVnd(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.End,
                        color = OnSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = summary.totalRevenue.formatVnd(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.End,
                        color = Primary,
                        modifier = Modifier.weight(1.1f)
                    )
                }
            }
        }
    }
}

