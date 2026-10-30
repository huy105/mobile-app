package com.example.canteen.ui.common.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Specific shapes mapped 1-1 from design tokens
val ShapeCard = RoundedCornerShape(16.dp)
val ShapeButton = RoundedCornerShape(12.dp)
val ShapeThumbnail = RoundedCornerShape(12.dp)
val ShapeBottomSheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
val ShapeCodeBanner = RoundedCornerShape(24.dp)
val ShapePinBox = RoundedCornerShape(14.dp)
val ShapeChip = RoundedCornerShape(999.dp)
val ShapePill = ShapeChip

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = ShapeButton,
    medium = ShapeCard,
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

