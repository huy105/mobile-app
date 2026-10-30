package com.example.canteen.ui.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canteen.ui.common.theme.Error
import com.example.canteen.ui.common.theme.ErrorContainer
import androidx.compose.foundation.background

/** Pill button with icon + label so logout is easy to spot (see docs/design/design.html). */
@Composable
fun LogoutButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(ErrorContainer)
            .border(BorderStroke(1.5.dp, Error), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "⎋", fontSize = 16.sp, color = Error)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Đăng xuất",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Error
        )
    }
}
