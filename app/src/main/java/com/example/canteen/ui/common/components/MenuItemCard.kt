package com.example.canteen.ui.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.canteen.ui.common.theme.Error
import com.example.canteen.ui.common.theme.ErrorContainer
import com.example.canteen.ui.common.theme.OnPrimary
import com.example.canteen.ui.common.theme.OnSurface
import com.example.canteen.ui.common.theme.OnSurfaceVariant
import com.example.canteen.ui.common.theme.Outline
import com.example.canteen.ui.common.theme.Primary
import com.example.canteen.ui.common.theme.PrimaryContainer
import com.example.canteen.ui.common.theme.ShapeCard
import com.example.canteen.ui.common.theme.ShapeThumbnail
import com.example.canteen.ui.common.theme.Surface
import com.example.canteen.ui.common.util.formatVnd
import com.example.canteen.ui.common.util.getCategoryIcon

@Composable
fun MenuItemCard(
    name: String,
    description: String,
    price: Long,
    imageUrl: String,
    category: String,
    inStock: Boolean,
    quantityInCart: Int,
    onAddToCart: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailSize: Dp = 72.dp
) {
    val cardAlpha = if (inStock) 1f else 0.45f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCard)
            .background(Surface)
            .border(1.dp, Outline, ShapeCard)
            .padding(12.dp)
            .alpha(cardAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with Coil AsyncImage + category fallback
        Box(
            modifier = Modifier
                .size(thumbnailSize)
                .clip(ShapeThumbnail)
                .background(PrimaryContainer),
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(thumbnailSize),
                loading = {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(thumbnailSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = getCategoryIcon(category),
                            fontSize = if (thumbnailSize > 60.dp) 34.sp else 26.sp
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(thumbnailSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = getCategoryIcon(category),
                            fontSize = if (thumbnailSize > 60.dp) 34.sp else 26.sp
                        )
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
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!inStock) {
                Spacer(modifier = Modifier.height(2.dp))
                RawTag(
                    label = "Hết món",
                    backgroundColor = ErrorContainer,
                    textColor = Error
                )
            } else if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = price.formatVnd(),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Primary
            )
        }

        // Action button or Stepper
        if (!inStock) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Outline),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = Color(0xFFA8A29E),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (quantityInCart > 0) {
            QuantityStepper(
                quantity = quantityInCart,
                onIncrease = onIncrease,
                onDecrease = onDecrease
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Primary)
                    .clickable { onAddToCart() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = OnPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
