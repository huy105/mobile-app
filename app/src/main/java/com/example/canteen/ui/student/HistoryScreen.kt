package com.example.canteen.ui.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canteen.data.model.Order
import com.example.canteen.ui.common.components.EmptyState
import com.example.canteen.ui.common.components.LoadingBox
import com.example.canteen.ui.common.components.StatusTag
import com.example.canteen.ui.common.theme.Background
import com.example.canteen.ui.common.theme.OnSurface
import com.example.canteen.ui.common.theme.OnSurfaceVariant
import com.example.canteen.ui.common.theme.Outline
import com.example.canteen.ui.common.theme.Primary
import com.example.canteen.ui.common.theme.ShapeCard
import com.example.canteen.ui.common.theme.Surface
import com.example.canteen.ui.common.util.formatVnd

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateToOrder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    when {
        uiState.isLoading -> {
            LoadingBox(message = "Đang tải lịch sử đơn hàng...")
        }
        uiState.orders.isEmpty() -> {
            EmptyState(
                icon = "🧾",
                title = "Bạn chưa có đơn nào",
                description = "Các đơn đã đặt sẽ xuất hiện tại đây.",
                modifier = modifier.fillMaxSize()
            )
        }
        else -> {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .background(Background)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = uiState.orders, key = { it.id }) { order ->
                    OrderHistoryCard(
                        order = order,
                        onClick = { onNavigateToOrder(order.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun OrderHistoryCard(
    order: Order,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalItems = order.items.sumOf { it.quantity }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCard)
            .background(Surface)
            .border(1.dp, Outline, ShapeCard)
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#${order.pickupCode}",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = OnSurface,
                letterSpacing = 2.sp
            )
            StatusTag(status = order.status)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "${order.slotLabel} · $totalItems món",
            fontSize = 13.sp,
            color = OnSurfaceVariant
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = order.total.formatVnd(),
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Primary
        )
    }
}

