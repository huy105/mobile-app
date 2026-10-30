package com.example.canteen.ui.common.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canteen.ui.common.theme.Error
import com.example.canteen.ui.common.theme.OnPrimary
import com.example.canteen.ui.common.theme.OnSurface
import com.example.canteen.ui.common.theme.Outline
import com.example.canteen.ui.common.theme.Primary
import com.example.canteen.ui.common.theme.ShapePinBox
import com.example.canteen.ui.common.theme.Surface

@Composable
fun PinDisplay(
    code: String,
    maxLength: Int = 4,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        for (i in 0 until maxLength) {
            val char = code.getOrNull(i)?.toString() ?: ""
            val isCurrent = i == code.length

            val borderColor by animateColorAsState(
                targetValue = when {
                    isError -> Error
                    isCurrent -> Primary
                    else -> Outline
                },
                label = "pinBorder"
            )

            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 68.dp)
                    .clip(ShapePinBox)
                    .background(Surface)
                    .border(2.dp, borderColor, ShapePinBox),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = char,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = OnSurface
                )
            }
        }
    }
}

@Composable
fun NumberKeypad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("⌫", "0", "✓")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (key in row) {
                    val isConfirm = key == "✓"
                    val isDelete = key == "⌫"

                    val bg = if (isConfirm) Primary else Surface
                    val border = if (isConfirm) Primary else Outline
                    val textColor = when {
                        isConfirm -> OnPrimary
                        isDelete -> Error
                        else -> OnSurface
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(ShapePinBox)
                            .background(bg)
                            .border(1.dp, border, ShapePinBox)
                            .clickable {
                                when {
                                    isDelete -> onDelete()
                                    isConfirm -> onConfirm()
                                    else -> onDigit(key[0])
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}
