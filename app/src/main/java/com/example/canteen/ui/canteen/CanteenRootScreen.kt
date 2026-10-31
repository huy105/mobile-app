package com.example.canteen.ui.canteen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canteen.data.model.User
import com.example.canteen.ui.common.components.LogoutButton
import com.example.canteen.ui.common.components.RawTag
import com.example.canteen.ui.common.theme.Background
import com.example.canteen.ui.common.theme.OnPrimaryContainer
import com.example.canteen.ui.common.theme.OnSurface
import com.example.canteen.ui.common.theme.OnSurfaceVariant
import com.example.canteen.ui.common.theme.Outline
import com.example.canteen.ui.common.theme.PrimaryContainer
import com.example.canteen.ui.common.theme.Success
import com.example.canteen.ui.common.theme.SuccessContainer
import com.example.canteen.ui.common.theme.Surface

@Composable
fun CanteenRootScreen(
    user: User,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0: Tổng quan, 1: Đơn hàng, 2: Thực đơn, 3: Trả đồ

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTabletLandscape = maxWidth >= 600.dp

        Scaffold(
            topBar = {
                CanteenTopBar(
                    userName = user.name,
                    isCompact = !isTabletLandscape,
                    onLogout = onLogout
                )
            },
            bottomBar = {
                if (!isTabletLandscape) {
                    CanteenBottomBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            },
            containerColor = Background
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTabletLandscape) {
                    CanteenNavigationRail(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (selectedTab) {
                        0 -> DashboardScreen(
                            onNavigateToOrders = { selectedTab = 1 },
                            onNavigateToMenu = { selectedTab = 2 }
                        )
                        1 -> CanteenOrdersScreen()
                        2 -> CanteenMenuScreen()
                        3 -> CanteenVerifyScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun CanteenTopBar(
    userName: String = "",
    isCompact: Boolean = false,
    onLogout: () -> Unit
) {
    val name = userName.ifBlank { "Căng tin Quầy 1" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(Surface)
            .border(width = 1.dp, color = Outline)
            .padding(horizontal = if (isCompact) 16.dp else 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "🍱 $name",
                fontSize = if (isCompact) 18.sp else 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isCompact) {
                // Status goes under the title on phones so it never squeezes the logout button
                Text(
                    text = "● Đang nhận đơn",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Success,
                    maxLines = 1
                )
            }
        }

        if (!isCompact) {
            RawTag(
                label = "● Đang nhận đơn",
                backgroundColor = SuccessContainer,
                textColor = Success,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }

        LogoutButton(onClick = onLogout)
    }
}

@Composable
fun CanteenNavigationRail(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val items = listOf(
        Triple(0, "📊", "Tổng quan"),
        Triple(1, "📋", "Đơn hàng"),
        Triple(2, "🍽", "Thực đơn"),
        Triple(3, "✅", "Trả đồ")
    )

    Column(
        modifier = Modifier
            .width(96.dp)
            .fillMaxHeight()
            .background(Surface)
            .border(width = 1.dp, color = Outline)
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for ((index, icon, label) in items) {
            val isSelected = selectedTab == index
            val contentColor = if (isSelected) OnPrimaryContainer else OnSurfaceVariant

            Column(
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) PrimaryContainer else Color.Transparent)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = icon,
                    fontSize = 22.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun CanteenBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Surface)
            .border(width = 1.dp, color = Outline)
    ) {
        val items = listOf(
            Triple(0, "📊", "Tổng quan"),
            Triple(1, "📋", "Đơn hàng"),
            Triple(2, "🍽", "Thực đơn"),
            Triple(3, "✅", "Trả đồ")
        )

        for ((index, icon, label) in items) {
            val isSelected = selectedTab == index
            val contentColor = if (isSelected) OnPrimaryContainer else OnSurfaceVariant

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onTabSelected(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
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

