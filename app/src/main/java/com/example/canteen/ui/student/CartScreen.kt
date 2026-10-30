package com.example.canteen.ui.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
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
import com.example.canteen.ui.common.components.EmptyState
import com.example.canteen.ui.common.components.PrimaryButton
import com.example.canteen.ui.common.components.QuantityStepper
import com.example.canteen.ui.common.theme.Background
import com.example.canteen.ui.common.theme.OnSurface
import com.example.canteen.ui.common.theme.OnSurfaceVariant
import com.example.canteen.ui.common.theme.Outline
import com.example.canteen.ui.common.theme.Primary
import com.example.canteen.ui.common.theme.PrimaryContainer
import com.example.canteen.ui.common.theme.ShapeCard
import com.example.canteen.ui.common.theme.ShapeThumbnail
import com.example.canteen.ui.common.theme.Surface
import com.example.canteen.ui.common.util.formatVnd
import com.example.canteen.ui.common.util.getCategoryIcon

@Composable
fun CartScreen(
    viewModel: CartViewModel,
    onNavigateToMenu: () -> Unit,
    onNavigateToSlotPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cartState by viewModel.cartState.collectAsState()

    if (cartState.items.isEmpty()) {
        EmptyState(
            icon = "🛒",
            title = "Giỏ hàng trống",
            description = "Hãy chọn món ăn yêu thích từ thực đơn nhé!",
            actionText = "Xem thực đơn",
            onActionClick = onNavigateToMenu,
            modifier = modifier.fillMaxSize()
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = cartState.itemList, key = { it.menuItemId }) { item ->
                    CartItemRow(
                        name = item.name,
                        price = item.price,
                        quantity = item.quantity,
                        onIncrease = { viewModel.increaseQuantity(item.menuItemId) },
                        onDecrease = { viewModel.decreaseQuantity(item.menuItemId) }
                    )
                }

                // Summary Card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeCard)
                            .background(Surface)
                            .border(1.dp, Outline, ShapeCard)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Tạm tính (${cartState.totalCount} món)",
                                fontSize = 14.sp,
                                color = OnSurfaceVariant
                            )
                            Text(
                                text = cartState.totalPrice.formatVnd(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Phí dịch vụ",
                                fontSize = 14.sp,
                                color = OnSurfaceVariant
                            )
                            Text(
                                text = "0đ",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurface
                            )
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
                                text = cartState.totalPrice.formatVnd(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Primary
                            )
                        }
                    }
                }
            }

            // Footer with action button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .border(width = 1.dp, color = Outline)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                PrimaryButton(
                    text = "Chọn giờ nhận →",
                    onClick = onNavigateToSlotPicker,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun CartItemRow(
    name: String,
    price: Long,
    quantity: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCard)
            .background(Surface)
            .border(1.dp, Outline, ShapeCard)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(ShapeThumbnail)
                .background(PrimaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = getCategoryIcon(name),
                fontSize = 26.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = price.formatVnd(),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Primary
            )
        }

        // Stepper
        QuantityStepper(
            quantity = quantity,
            onIncrease = onIncrease,
            onDecrease = onDecrease
        )
    }
}

