package com.example.canteen.ui.canteen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    var itemToEditQuota by remember { mutableStateOf<MenuItem?>(null) }

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
                                onToggle = { viewModel.toggleStock(item) },
                                onEditQuota = { itemToEditQuota = item }
                            )
                        }
                    }
                }
            }
        }

        // Quota edit dialog
        itemToEditQuota?.let { item ->
            var quotaInput by remember(item.id) {
                mutableStateOf(if (item.dailyQuota > 0) item.dailyQuota.toString() else "0")
            }

            AlertDialog(
                onDismissRequest = { itemToEditQuota = null },
                title = {
                    Text(
                        text = "Sửa số suất: ${item.name}",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        OutlinedTextField(
                            value = quotaInput,
                            onValueChange = { quotaInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Số suất mỗi ngày") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Nhập 0 nếu không giới hạn số suất.",
                            fontSize = 12.sp,
                            color = OnSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val quota = quotaInput.toIntOrNull() ?: 0
                            viewModel.updateQuota(item.id, quota)
                            itemToEditQuota = null
                        }
                    ) {
                        Text("Lưu", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToEditQuota = null }) {
                        Text("Hủy")
                    }
                }
            )
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
    onToggle: () -> Unit,
    onEditQuota: () -> Unit
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

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val quotaText = if (item.dailyQuota > 0) "Đã bán ${item.soldQty}/${item.dailyQuota} suất" else "Không giới hạn"
                Text(
                    text = quotaText,
                    fontSize = 12.sp,
                    color = OnSurfaceVariant
                )
                Text(
                    text = "· Sửa suất",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    modifier = Modifier.clickable { onEditQuota() }
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

