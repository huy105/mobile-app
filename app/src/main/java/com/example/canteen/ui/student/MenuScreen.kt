package com.example.canteen.ui.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.example.canteen.ui.common.components.LoadingBox
import com.example.canteen.ui.common.components.MenuItemCard
import com.example.canteen.ui.common.components.PrimaryButton
import com.example.canteen.ui.common.theme.Background
import com.example.canteen.ui.common.theme.OnPrimary
import com.example.canteen.ui.common.theme.OnSurface
import com.example.canteen.ui.common.theme.OnSurfaceVariant
import com.example.canteen.ui.common.theme.Outline
import com.example.canteen.ui.common.theme.Primary
import com.example.canteen.ui.common.theme.Surface
import com.example.canteen.ui.common.util.formatVnd

@Composable
fun MenuScreen(
    viewModel: MenuViewModel,
    onNavigateToCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Category horizontal chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (cat in uiState.categories) {
                    val isSelected = uiState.selectedCategory.equals(cat, ignoreCase = true)
                    val bg = if (isSelected) Primary else Surface
                    val textColor = if (isSelected) OnPrimary else OnSurface
                    val borderColor = if (isSelected) Primary else Outline

                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(bg)
                            .border(1.5.dp, borderColor, CircleShape)
                            .clickable { viewModel.selectCategory(cat) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor
                        )
                    }
                }
            }

            // Body content
            when {
                uiState.isLoading -> {
                    LoadingBox(message = "Đang tải thực đơn...")
                }
                uiState.filteredItems.isEmpty() -> {
                    EmptyState(
                        icon = "🍽",
                        title = "Hôm nay chưa có món",
                        description = "Căng tin đang chuẩn bị món ăn, bạn vui lòng quay lại sau nhé!"
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = if (uiState.cartState.totalCount > 0) 80.dp else 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        uiState.groupedItems.forEach { (categoryName, itemList) ->
                            item(key = "header_$categoryName") {
                                Text(
                                    text = categoryName.uppercase(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceVariant,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                )
                            }

                            items(items = itemList, key = { it.id }) { item ->
                                val quantityInCart = uiState.cartState.items[item.id]?.quantity ?: 0
                                MenuItemCard(
                                    name = item.name,
                                    description = item.description,
                                    price = item.price,
                                    imageUrl = item.imageUrl,
                                    category = item.category,
                                    inStock = item.inStock,
                                    quantityInCart = quantityInCart,
                                    onAddToCart = { viewModel.addToCart(item) },
                                    onIncrease = { viewModel.increaseQuantity(item.id) },
                                    onDecrease = { viewModel.decreaseQuantity(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Cart Summary Bar above Bottom Nav
        if (uiState.cartState.totalCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                PrimaryButton(
                    text = "",
                    onClick = onNavigateToCart,
                    modifier = Modifier.fillMaxWidth(),
                    leadingContent = {
                        Text(
                            text = "🛒 ${uiState.cartState.totalCount} món",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnPrimary
                        )
                    },
                    trailingContent = {
                        Text(
                            text = uiState.cartState.totalPrice.formatVnd(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = OnPrimary
                        )
                    }
                )
            }
        }
    }
}

