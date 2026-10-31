package com.example.canteen.ui.common.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canteen.R
import com.example.canteen.ui.common.theme.OnSurface
import com.example.canteen.ui.common.theme.OnSurfaceVariant
import com.example.canteen.ui.common.theme.Outline
import com.example.canteen.ui.common.theme.Primary
import com.example.canteen.ui.common.theme.Success
import com.example.canteen.ui.common.theme.SuccessContainer
import com.example.canteen.ui.common.theme.Surface
import com.example.canteen.ui.common.theme.SurfaceVariant
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun formatChipDate(date: LocalDate): String {
    val dayOfWeek = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "Thứ Hai"
        DayOfWeek.TUESDAY -> "Thứ Ba"
        DayOfWeek.WEDNESDAY -> "Thứ Tư"
        DayOfWeek.THURSDAY -> "Thứ Năm"
        DayOfWeek.FRIDAY -> "Thứ Sáu"
        DayOfWeek.SATURDAY -> "Thứ Bảy"
        DayOfWeek.SUNDAY -> "Chủ Nhật"
        null -> ""
    }
    val dateFormatted = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    return "📅 $dayOfWeek, $dateFormatted"
}

@Composable
fun DateBar(
    selectedDate: LocalDate,
    isTablet: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onResetToToday: () -> Unit,
    onOpenDatePicker: () -> Unit,
    modifier: Modifier = Modifier,
    isToday: Boolean = selectedDate.isEqual(LocalDate.now()),
    formattedDate: String = formatChipDate(selectedDate),
    hasOrders: Boolean = true,
    emptyOrdersMessage: String? = null,
    rightContent: @Composable (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Previous day button ‹
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.5.dp, Outline), RoundedCornerShape(12.dp))
                    .background(Surface)
                    .clickable(onClick = onPreviousDay),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "‹",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            }

            // Central Date Chip (dchip)
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.5.dp, Outline), RoundedCornerShape(12.dp))
                    .background(Surface)
                    .clickable(onClick = onOpenDatePicker)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formattedDate,
                        fontSize = if (isTablet) 15.sp else 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isToday) {
                        RawTag(
                            label = stringResource(R.string.tag_today),
                            backgroundColor = SuccessContainer,
                            textColor = Success
                        )
                    } else {
                        RawTag(
                            label = stringResource(R.string.tag_past_day),
                            backgroundColor = SurfaceVariant,
                            textColor = OnSurfaceVariant
                        )
                    }
                }
            }

            // Next day button › (disabled & dimmed when today)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        BorderStroke(1.5.dp, if (isToday) Outline.copy(alpha = 0.5f) else Outline),
                        RoundedCornerShape(12.dp)
                    )
                    .background(Surface)
                    .alpha(if (isToday) 0.4f else 1f)
                    .clickable(enabled = !isToday, onClick = onNextDay),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "›",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isToday) OnSurfaceVariant.copy(alpha = 0.5f) else OnSurface
                )
            }

            // Quick "Hôm nay" button (only visible when !isToday)
            if (!isToday) {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.5.dp, Primary), RoundedCornerShape(12.dp))
                        .background(Surface)
                        .clickable(onClick = onResetToToday)
                        .padding(horizontal = if (isTablet) 14.dp else 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.btn_today),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }
            }

            if (isTablet) {
                Spacer(modifier = Modifier.weight(1f))
                rightContent?.invoke()
            }
        }

        if (!hasOrders && !emptyOrdersMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = emptyOrdersMessage,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonDatePickerDialog(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMillis = remember(selectedDate) {
        selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return !date.isAfter(LocalDate.now())
            }

            override fun isSelectableYear(year: Int): Boolean {
                return year <= LocalDate.now().year
            }
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateSelected(picked)
                    }
                    onDismiss()
                }
            ) {
                Text("Chọn", color = Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = OnSurfaceVariant)
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

