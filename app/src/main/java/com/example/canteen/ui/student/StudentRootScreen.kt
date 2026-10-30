package com.example.canteen.ui.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.canteen.data.model.User
import com.example.canteen.ui.common.theme.Background
import com.example.canteen.ui.common.theme.OnPrimary
import com.example.canteen.ui.common.theme.OnPrimaryContainer
import com.example.canteen.ui.common.theme.OnSurface
import com.example.canteen.ui.common.theme.OnSurfaceVariant
import com.example.canteen.ui.common.theme.Outline
import com.example.canteen.ui.common.theme.Primary
import com.example.canteen.ui.common.theme.PrimaryContainer
import com.example.canteen.ui.common.theme.Surface

@Composable
fun StudentRootScreen(
    user: User,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSlotPicker: () -> Unit = {},
    onNavigateToOrderStatus: (String) -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0: Menu, 1: Cart, 2: History

    val menuViewModel: MenuViewModel = hiltViewModel()
    val cartViewModel: CartViewModel = hiltViewModel()
    val historyViewModel: HistoryViewModel = hiltViewModel()

    val cartState by cartViewModel.cartState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            StudentTopBar(
                userName = user.name.ifBlank { "Học sinh Demo" },
                selectedTab = selectedTab,
                onLogout = onLogout
            )
        },
        bottomBar = {
            StudentBottomBar(
                selectedTab = selectedTab,
                cartItemCount = cartState.totalCount,
                onTabSelected = { selectedTab = it }
            )
        },
        containerColor = Background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> {
                    MenuScreen(
                        viewModel = menuViewModel,
                        onNavigateToCart = { selectedTab = 1 }
                    )
                }
                1 -> {
                    CartScreen(
                        viewModel = cartViewModel,
                        onNavigateToMenu = { selectedTab = 0 },
                        onNavigateToSlotPicker = onNavigateToSlotPicker
                    )
                }
                2 -> {
                    HistoryScreen(
                        viewModel = historyViewModel,
                        onNavigateToOrder = onNavigateToOrderStatus
                    )
                }
            }
        }
    }
}

@Composable
fun StudentTopBar(
    userName: String,
    selectedTab: Int,
    onLogout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Background)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (selectedTab == 0) {
                Text(
                    text = "Xin chào, $userName",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant
                )
                Text(
                    text = "Thực đơn hôm nay",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            } else if (selectedTab == 1) {
                Text(
                    text = "Giỏ hàng",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            } else {
                Text(
                    text = "Đơn của tôi",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            }
        }

        // Logout icon button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { onLogout() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⎋",
                fontSize = 20.sp,
                color = OnSurface
            )
        }
    }
}

@Composable
fun StudentBottomBar(
    selectedTab: Int,
    cartItemCount: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Surface)
            .border(width = 1.dp, color = Outline)
    ) {
        val tabs = listOf(
            Triple(0, "🍽", "Menu"),
            Triple(1, "🛒", "Giỏ hàng"),
            Triple(2, "🧾", "Đơn của tôi")
        )

        for ((index, icon, label) in tabs) {
            val isSelected = selectedTab == index
            val contentColor = if (isSelected) OnPrimaryContainer else OnSurfaceVariant

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onTabSelected(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) PrimaryContainer else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = icon,
                        fontSize = 18.sp
                    )

                    // Badge for Cart
                    if (index == 1 && cartItemCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (cartItemCount > 99) "99+" else cartItemCount.toString(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnPrimary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }
    }
}
