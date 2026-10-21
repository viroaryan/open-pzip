package app.pzip.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pzip.ui.theme.P
import app.pzip.ui.theme.Serif
import kotlin.math.cos
import kotlin.math.sin

/** Ray lengths of the pzip spark, the same shape as the launcher icon. */
private val RAYS = floatArrayOf(26f, 18f, 23f, 16f, 25f, 19f, 24f, 17f, 26f, 18f, 22f, 16f)

@Composable
fun PzipSpark(size: Dp, color: Color = P.c.accent, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 56f
        val cx = this.size.width / 2
        val cy = this.size.height / 2
        RAYS.forEachIndexed { i, len ->
            val a = Math.toRadians((i * 30 - 90).toDouble())
            val inner = 7.5f * s
            drawLine(
                color = color,
                start = Offset(cx + inner * cos(a).toFloat(), cy + inner * sin(a).toFloat()),
                end = Offset(cx + len * s * cos(a).toFloat(), cy + len * s * sin(a).toFloat()),
                strokeWidth = 4.4f * s,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun Wordmark(modifier: Modifier = Modifier, size: Int = 26) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PzipSpark(size = (size * 1.15f).dp)
        Text("pzip", style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = size.sp, letterSpacing = (-0.5).sp, color = P.c.text))
    }
}
