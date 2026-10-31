package com.example.canteen.ui.student

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.canteen.data.model.MenuItem
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
import com.example.canteen.ui.common.theme.PrimaryContainer
import com.example.canteen.ui.common.theme.Surface
import com.example.canteen.ui.common.util.formatVnd
import com.example.canteen.ui.common.util.getCategoryIcon
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MenuScreen(
    viewModel: MenuViewModel,
    onNavigateToCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

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
                    val popularItems = remember(uiState.items) {
                        uiState.items
                            .filter { it.inStock && it.soldQty >= 10 }
                            .sortedByDescending { it.soldQty }
                            .take(5)
                    }

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
                        if (popularItems.isNotEmpty()) {
                            item(key = "header_popular") {
                                Text(
                                    text = "🔥 Gợi ý hôm nay",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                )
                            }
                            item(key = "row_popular") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    items(popularItems, key = { "pop_${it.id}" }) { popItem ->
                                        PopularItemCard(
                                            item = popItem,
                                            onAddToCart = { viewModel.addToCart(popItem) }
                                        )
                                    }
                                }
                            }
                        }

                        var globalIndex = 0
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
                                val index = globalIndex++
                                val quantityInCart = uiState.cartState.items[item.id]?.quantity ?: 0
                                val itemVisible = remember { mutableStateOf(index >= 8) }
                                if (index < 8) {
                                    LaunchedEffect(item.id) {
                                        delay(index * 40L)
                                        itemVisible.value = true
                                    }
                                }

                                AnimatedVisibility(
                                    visible = itemVisible.value,
                                    enter = fadeIn(tween(250)) + slideInVertically(
                                        animationSpec = tween(250),
                                        initialOffsetY = { 20 }
                                    ),
                                    modifier = Modifier.animateItemPlacement()
                                ) {
                                    MenuItemCard(
                                        name = item.name,
                                        description = item.description,
                                        price = item.price,
                                        imageUrl = item.imageUrl,
                                        category = item.category,
                                        inStock = item.inStock,
                                        quantityInCart = quantityInCart,
                                        remainingQty = item.remainingQty,
                                        isPopular = item.isPopular,
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (uiState.cartState.totalCount > 0) 72.dp else 16.dp)
        )
    }
}

@Composable
private fun PopularItemCard(
    item: MenuItem,
    onAddToCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Outline, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(PrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(getCategoryIcon(item.category), fontSize = 28.sp)
                        }
                    },
                    error = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(getCategoryIcon(item.category), fontSize = 28.sp)
                        }
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    text = item.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Đã đặt ${item.soldQty} suất",
                    fontSize = 11.sp,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.price.formatVnd(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Primary,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Primary)
                            .clickable { onAddToCart() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            color = OnPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}


