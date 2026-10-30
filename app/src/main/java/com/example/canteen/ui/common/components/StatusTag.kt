package com.example.canteen.ui.common.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canteen.ui.common.theme.Error
import com.example.canteen.ui.common.theme.ErrorContainer
import com.example.canteen.ui.common.theme.Info
import com.example.canteen.ui.common.theme.InfoContainer
import com.example.canteen.ui.common.theme.OnSecondaryContainer
import com.example.canteen.ui.common.theme.OnSurfaceVariant
import com.example.canteen.ui.common.theme.SecondaryContainer
import com.example.canteen.ui.common.theme.Success
import com.example.canteen.ui.common.theme.SuccessContainer
import com.example.canteen.ui.common.theme.SurfaceVariant

@Composable
fun StatusTag(
    status: String,
    modifier: Modifier = Modifier
) {
    val (label, targetBg, targetText) = when (status.uppercase()) {
        "RECEIVED" -> Triple("Đã nhận", InfoContainer, Info)
        "PREPARING" -> Triple("Đang chuẩn bị", SecondaryContainer, OnSecondaryContainer)
        "READY" -> Triple("Sẵn sàng", SuccessContainer, Success)
        "COMPLETED" -> Triple("Hoàn thành", SurfaceVariant, OnSurfaceVariant)
        "CANCELLED" -> Triple("Đã hủy", ErrorContainer, Error)
        else -> Triple(status, SurfaceVariant, OnSurfaceVariant)
    }

    RawTag(
        label = label,
        backgroundColor = targetBg,
        textColor = targetText,
        modifier = modifier
    )
}

@Composable
fun RawTag(
    label: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedBg by animateColorAsState(
        targetValue = backgroundColor,
        animationSpec = tween(200),
        label = "tagBg"
    )
    val animatedText by animateColorAsState(
        targetValue = textColor,
        animationSpec = tween(200),
        label = "tagText"
    )

    Box(
        modifier = modifier
            .background(color = animatedBg, shape = CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = animatedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 14.sp
        )
    }
}

