package com.example.canteen.ui.common.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canteen.ui.common.theme.Error
import com.example.canteen.ui.common.theme.ErrorContainer
import com.example.canteen.ui.common.theme.OnPrimary
import com.example.canteen.ui.common.theme.Outline
import com.example.canteen.ui.common.theme.Primary
import com.example.canteen.ui.common.theme.ShapeButton
import com.example.canteen.ui.common.theme.Success

enum class ButtonStyle {
    PRIMARY,
    GHOST,
    DANGER,
    SUCCESS
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    isSmall: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val height: Dp = if (isSmall) 36.dp else 48.dp
    val fontSize: TextUnit = if (isSmall) 13.sp else 15.sp

    val targetBg = when (style) {
        ButtonStyle.PRIMARY -> Primary
        ButtonStyle.GHOST -> Color.Transparent
        ButtonStyle.DANGER -> ErrorContainer
        ButtonStyle.SUCCESS -> Success
    }
    val targetContent = when (style) {
        ButtonStyle.PRIMARY -> OnPrimary
        ButtonStyle.GHOST -> Primary
        ButtonStyle.DANGER -> Error
        ButtonStyle.SUCCESS -> OnPrimary
    }

    val backgroundColor by animateColorAsState(targetValue = targetBg, label = "btnBg")
    val contentColor by animateColorAsState(targetValue = targetContent, label = "btnContent")

    val disabledBg = Outline
    val disabledContent = Color(0xFFA8A29E)

    if (style == ButtonStyle.GHOST) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(height),
            enabled = enabled && !isLoading,
            shape = ShapeButton,
            border = BorderStroke(1.5.dp, if (enabled) Primary else Outline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Primary,
                disabledContentColor = disabledContent
            )
        ) {
            ButtonBody(text, isLoading, if (enabled) Primary else disabledContent, fontSize, leadingContent, trailingContent)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.height(height),
            enabled = enabled && !isLoading,
            shape = ShapeButton,
            colors = ButtonDefaults.buttonColors(
                containerColor = backgroundColor,
                contentColor = contentColor,
                disabledContainerColor = disabledBg,
                disabledContentColor = disabledContent
            )
        ) {
            ButtonBody(text, isLoading, if (enabled) contentColor else disabledContent, fontSize, leadingContent, trailingContent)
        }
    }
}

@Composable
private fun ButtonBody(
    text: String,
    isLoading: Boolean,
    color: Color,
    fontSize: TextUnit,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = color
        )
    } else {
        Box(contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leading != null) {
                    leading()
                    Spacer(modifier = Modifier.size(6.dp))
                }
                Text(
                    text = text,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                if (trailing != null) {
                    Spacer(modifier = Modifier.size(6.dp))
                    trailing()
                }
            }
        }
    }
}

