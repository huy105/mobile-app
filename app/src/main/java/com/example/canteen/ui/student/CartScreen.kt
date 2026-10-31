package com.example.canteen.ui.student

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canteen.ui.common.components.EmptyState
import com.example.canteen.ui.common.components.PrimaryButton
import com.example.canteen.ui.common.components.QuantityStepper
import com.example.canteen.ui.common.theme.Background
import com.example.canteen.ui.common.theme.Error
import com.example.canteen.ui.common.theme.OnPrimary
import com.example.canteen.ui.common.theme.OnPrimaryContainer
import com.example.canteen.ui.common.theme.OnSurface
import com.example.canteen.ui.common.theme.OnSurfaceVariant
import com.example.canteen.ui.common.theme.Outline
import com.example.canteen.ui.common.theme.Primary
import com.example.canteen.ui.common.theme.PrimaryContainer
import com.example.canteen.ui.common.theme.ShapeCard
import com.example.canteen.ui.common.theme.ShapeThumbnail
import com.example.canteen.ui.common.theme.Success
import com.example.canteen.ui.common.theme.Surface
import com.example.canteen.ui.common.util.formatVnd
import com.example.canteen.ui.common.util.getCategoryIcon

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CartScreen(
    viewModel: CartViewModel,
    onNavigateToMenu: () -> Unit,
    onNavigateToSlotPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    val animatedSubtotal by animateIntAsState(
        targetValue = uiState.pricing.subtotal.toInt(),
        animationSpec = tween(300),
        label = "subtotalAnim"
    )
    val animatedTotal by animateIntAsState(
        targetValue = uiState.pricing.total.toInt(),
        animationSpec = tween(300),
        label = "totalAnim"
    )

    if (uiState.cartState.items.isEmpty()) {
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
                items(items = uiState.cartState.itemList, key = { it.menuItemId }) { item ->
                    CartItemRow(
                        name = item.name,
                        price = item.price,
                        quantity = item.quantity,
                        onIncrease = { viewModel.increaseQuantity(item.menuItemId) },
                        onDecrease = { viewModel.decreaseQuantity(item.menuItemId) },
                        modifier = Modifier.animateItemPlacement()
                    )
                }

                // Offers Card ("Ưu đãi", S3 Mockup)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeCard)
                            .background(Surface)
                            .border(1.dp, Outline, ShapeCard)
                            .padding(14.dp)
                    ) {
                        // Voucher input + Apply button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = uiState.voucherInput,
                                onValueChange = { viewModel.onVoucherInputChange(it) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                placeholder = {
                                    Text(
                                        text = "🏷️ Nhập mã voucher",
                                        fontSize = 13.sp,
                                        color = OnSurfaceVariant
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        viewModel.applyVoucher(uiState.voucherInput)
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Primary,
                                    unfocusedBorderColor = Outline,
                                    focusedContainerColor = Surface,
                                    unfocusedContainerColor = Surface
                                )
                            )

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.applyVoucher(uiState.voucherInput)
                                },
                                modifier = Modifier.height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Primary,
                                    contentColor = OnPrimary
                                )
                            ) {
                                Text(
                                    text = "Áp dụng",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Error message under input
                        if (!uiState.voucherError.isNullOrBlank()) {
                            Text(
                                text = uiState.voucherError ?: "",
                                color = Error,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }

                        // Available voucher chips
                        if (uiState.availableVouchers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (v in uiState.availableVouchers) {
                                    val isApplied = uiState.appliedVoucher?.code.equals(v.code, ignoreCase = true)
                                    val chipBg = if (isApplied) Primary else PrimaryContainer
                                    val chipTextColor = if (isApplied) OnPrimary else OnPrimaryContainer
                                    val chipBorder = if (isApplied) Primary else Primary

                                    val label = if (isApplied && uiState.pricing.voucherDiscount > 0L) {
                                        "${v.code} · -${uiState.pricing.voucherDiscount.formatVnd()}"
                                    } else {
                                        v.code
                                    }

                                    Box(
                                        modifier = Modifier
                                            .height(34.dp)
                                            .clip(CircleShape)
                                            .background(chipBg)
                                            .border(1.5.dp, chipBorder, CircleShape)
                                            .clickable { viewModel.applyVoucher(v.code) }
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = chipTextColor
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Outline, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Training Points Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "⭐ Dùng điểm rèn luyện",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface
                                )
                                val pointsSubtitle = when {
                                    uiState.userPoints <= 0 -> "Bạn chưa có điểm rèn luyện"
                                    uiState.cartState.usePoints && uiState.pricing.pointsUsed > 0 ->
                                        "Còn ${uiState.userPoints} điểm · Dùng ${uiState.pricing.pointsUsed} điểm, giảm ${uiState.pricing.pointsDiscount.formatVnd()}"
                                    else -> "Còn ${uiState.userPoints} điểm (≈ ${(uiState.userPoints * 500L).formatVnd()})"
                                }
                                Text(
                                    text = pointsSubtitle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = OnSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Switch(
                                checked = uiState.cartState.usePoints,
                                onCheckedChange = { viewModel.toggleUsePoints(it) },
                                enabled = uiState.canUsePoints,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = OnPrimary,
                                    checkedTrackColor = Primary,
                                    uncheckedThumbColor = OnSurfaceVariant,
                                    uncheckedTrackColor = Surface
                                )
                            )
                        }
                    }
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
                        // Subtotal
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Tạm tính (${uiState.cartState.totalCount} món)",
                                fontSize = 14.sp,
                                color = OnSurfaceVariant
                            )
                            Text(
                                text = animatedSubtotal.toLong().formatVnd(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurface
                            )
                        }

                        // Voucher discount (if applied)
                        if (uiState.pricing.voucherDiscount > 0L) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val vCode = uiState.appliedVoucher?.code ?: uiState.cartState.voucherCode ?: ""
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Voucher $vCode",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Success
                                )
                                Text(
                                    text = "-${uiState.pricing.voucherDiscount.formatVnd()}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Success
                                )
                            }
                        }

                        // Points discount (if applied)
                        if (uiState.pricing.pointsDiscount > 0L) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Điểm rèn luyện (${uiState.pricing.pointsUsed} điểm)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Success
                                )
                                Text(
                                    text = "-${uiState.pricing.pointsDiscount.formatVnd()}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Success
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Outline, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Total
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
                                text = animatedTotal.toLong().formatVnd(),
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
