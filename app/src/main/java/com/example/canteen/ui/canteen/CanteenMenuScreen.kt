package com.example.canteen.ui.canteen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.canteen.data.model.MenuItem
import com.example.canteen.ui.common.components.EmptyState
import com.example.canteen.ui.common.components.LoadingBox
import com.example.canteen.ui.common.components.RawTag
import com.example.canteen.ui.common.theme.Background
import com.example.canteen.ui.common.theme.Error
import com.example.canteen.ui.common.theme.ErrorContainer
import com.example.canteen.ui.common.theme.OnSurface
import com.example.canteen.ui.common.theme.OnSurfaceVariant
import com.example.canteen.ui.common.theme.Outline
import com.example.canteen.ui.common.theme.Primary
import com.example.canteen.ui.common.theme.PrimaryContainer
import com.example.canteen.ui.common.theme.ShapeCard
import com.example.canteen.ui.common.theme.ShapeThumbnail
import com.example.canteen.ui.common.theme.Success
import com.example.canteen.ui.common.theme.SuccessContainer
import com.example.canteen.ui.common.theme.Surface
import com.example.canteen.ui.common.util.formatVnd
import com.example.canteen.ui.common.util.getCategoryIcon

@Composable
fun CanteenMenuScreen(
    modifier: Modifier = Modifier,
    viewModel: CanteenMenuViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Background)) {
        if (uiState.isLoading) {
            LoadingBox(message = "Đang tải danh sách món...")
        } else if (uiState.items.isEmpty()) {
            EmptyState(
                icon = "🍽",
                title = "Chưa có món ăn nào trong thực đơn hôm nay"
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Header description
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Quản lý món hôm nay",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Gạt tắt khi hết hàng; thực đơn học sinh sẽ cập nhật ngay lập tức.",
                            fontSize = 13.sp,
                            color = OnSurfaceVariant
                        )
                    }

                    val inStockCount = uiState.items.count { it.inStock }
                    RawTag(
                        label = "Còn $inStockCount/${uiState.items.size} món",
                        backgroundColor = PrimaryContainer,
                        textColor = Primary
                    )
                }

                // Grid (2 columns on tablet, 1 column on phone)
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val columns = if (maxWidth >= 600.dp) 2 else 1

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(uiState.items, key = { it.id }) { item ->
                            CanteenMenuItemRow(
                                item = item,
                                onToggle = { viewModel.toggleStock(item) }
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
}

@Composable
private fun CanteenMenuItemRow(
    item: MenuItem,
    onToggle: () -> Unit
) {
    val thumbAlpha = if (item.inStock) 1f else 0.5f
    val textAlpha = if (item.inStock) 1f else 0.6f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCard)
            .background(Surface)
            .border(1.dp, Outline, ShapeCard)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail 56x56dp
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(ShapeThumbnail)
                .background(PrimaryContainer)
                .alpha(thumbAlpha),
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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = getCategoryIcon(item.category), fontSize = 24.sp)
                    }
                },
                error = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = getCategoryIcon(item.category), fontSize = 24.sp)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Info
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = item.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(textAlpha)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.inStock) {
                    RawTag(
                        label = "Còn món",
                        backgroundColor = SuccessContainer,
                        textColor = Success
                    )
                } else {
                    RawTag(
                        label = "Hết món",
                        backgroundColor = ErrorContainer,
                        textColor = Error
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = item.price.formatVnd(),
                    fontSize = 12.sp,
                    color = OnSurfaceVariant
                )
            }
        }

        // Custom Switch
        Switch(
            checked = item.inStock,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Success,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFD1D5DB),
                uncheckedBorderColor = Color(0xFF9CA3AF)
            )
        )
    }
}

