package es.sergiotech.tickettracker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import es.sergiotech.tickettracker.R

@Composable
internal fun BrandSymbol(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    if (dark) listOf(Color(0xFF123C46), Color(0xFF075760))
                    else listOf(Color(0xFF14B8BB), Color(0xFF00616D))
                )
            )
    ) {
        Image(painterResource(R.drawable.brand_mark), null, Modifier.fillMaxSize().padding(3.dp))
    }
}
