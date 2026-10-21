package app.pzip.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.cos
import kotlin.math.sin

enum class Pose { WAVE, WATCH, CHEER }

/**
 * A friendly flat-illustration character, drawn and animated in code (no image files):
 * breathing, blinking, waving and a softly bobbing head.
 */
@Composable
fun PersonArt(
    pose: Pose,
    modifier: Modifier = Modifier,
    skin: Color = Color(0xFFF1C7A5),
    hair: Color = Color(0xFF2B2420),
    shirt: Color = Color(0xFF8CC8F0),
    accent: Color = Color(0xFF4A9FD8),
    backdrop: Color = Color(0xFFE4F2FC),
) {
    val t = rememberInfiniteTransition(label = "person")
    val breathe by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val wave by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wave")
    val blink by t.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 4200
        1f at 0
        1f at 3900
        0.1f at 4000
        1f at 4100
    }), label = "blink")
    val orbit by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "orbit")

    Canvas(modifier) {
        // Work in a 300 x 300 design space, centered and scaled to fit.
        val unit = minOf(size.width, size.height) / 300f
        translate((size.width - 300 * unit) / 2, (size.height - 300 * unit) / 2) {
            scaleDraw(unit) {
                drawCircle(backdrop, 118f, Offset(150f, 160f))
                drawCircle(Color.White.copy(alpha = 0.55f), 22f, Offset(66f, 84f))
                drawCircle(Color.White.copy(alpha = 0.4f), 12f, Offset(246f, 230f))

                val bob = breathe * 3f
                body(shirt, skin, bob)
                head(skin, hair, blink, bob, pose)
                when (pose) {
                    Pose.WAVE -> {
                        phoneArm(skin, accent, bob)
                        waveArm(skin, shirt, wave, bob)
                    }
                    Pose.WATCH -> {
                        holdTablet(skin, accent, bob, breathe)
                    }
                    Pose.CHEER -> {
                        cheerArms(skin, shirt, wave, bob)
                    }
                }
                floaters(orbit, accent, pose)
            }
        }
    }
}

private inline fun DrawScope.scaleDraw(s: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.scale(s, s, Offset.Zero)
    block()
    drawContext.transform.scale(1 / s, 1 / s, Offset.Zero)
}

private fun DrawScope.body(shirt: Color, skin: Color, bob: Float) {
    // Neck
    drawRoundRect(skin, Offset(138f, 150f + bob), Size(24f, 26f), CornerRadius(10f))
    // Torso with rounded shoulders, cropped at the bottom like a portrait.
    val torso = Path().apply {
        moveTo(78f, 300f)
        cubicTo(78f, 214f, 100f, 172f + bob, 150f, 170f + bob)
        cubicTo(200f, 172f + bob, 222f, 214f, 222f, 300f)
        close()
    }
    drawPath(torso, shirt)
    // Collar
    val collar = Path().apply {
        moveTo(132f, 172f + bob)
        quadraticBezierTo(150f, 196f + bob, 168f, 172f + bob)
    }
    drawPath(collar, Color.White.copy(alpha = 0.7f), style = Stroke(5f, cap = StrokeCap.Round))
}

private fun DrawScope.head(skin: Color, hair: Color, blink: Float, bob: Float, pose: Pose) {
    val cy = 116f + bob
    // Ears
    drawCircle(skin, 9f, Offset(111f, cy + 4f))
    drawCircle(skin, 9f, Offset(189f, cy + 4f))
    // Face
    drawOval(skin, Offset(112f, cy - 42f), Size(76f, 86f))
    // Hair: soft swoopy cap
    val h = Path().apply {
        moveTo(110f, cy + 2f)
        cubicTo(102f, cy - 52f, 150f, cy - 70f, 182f, cy - 46f)
        cubicTo(198f, cy - 34f, 194f, cy - 6f, 190f, cy + 2f)
        cubicTo(184f, cy - 20f, 168f, cy - 30f, 146f, cy - 26f)
        cubicTo(132f, cy - 24f, 120f, cy - 14f, 110f, cy + 2f)
        close()
    }
    drawPath(h, hair)
    // Eyes (blink by squashing)
    val eyeH = 9f * blink
    drawOval(Color(0xFF1E1A17), Offset(132f, cy + 2f - eyeH / 2), Size(8f, eyeH.coerceAtLeast(1.5f)))
    drawOval(Color(0xFF1E1A17), Offset(160f, cy + 2f - eyeH / 2), Size(8f, eyeH.coerceAtLeast(1.5f)))
    // Cheeks
    drawCircle(Color(0xFFF29B8F).copy(alpha = 0.45f), 7f, Offset(128f, cy + 18f))
    drawCircle(Color(0xFFF29B8F).copy(alpha = 0.45f), 7f, Offset(172f, cy + 18f))
    // Smile (bigger when cheering)
    val smile = Path().apply {
        val w = if (pose == Pose.CHEER) 12f else 9f
        moveTo(150f - w, cy + 20f)
        quadraticBezierTo(150f, cy + 30f + (if (pose == Pose.CHEER) 4f else 0f), 150f + w, cy + 20f)
    }
    drawPath(smile, Color(0xFF7A3B2E), style = Stroke(3.2f, cap = StrokeCap.Round))
}

private fun DrawScope.arm(skin: Color, sleeve: Color, from: Offset, elbow: Offset, hand: Offset) {
    drawLine(sleeve, from, elbow, 22f, StrokeCap.Round)
    drawLine(skin, elbow, hand, 16f, StrokeCap.Round)
    drawCircle(skin, 11f, hand)
}

private fun DrawScope.phoneArm(skin: Color, accent: Color, bob: Float) {
    // Phone held in front of the chest
    val phoneTop = Offset(98f, 210f + bob)
    rotate(-10f, Offset(118f, 240f + bob)) {
        drawRoundRect(Color(0xFF141413), phoneTop, Size(42f, 70f), CornerRadius(9f))
        drawRoundRect(Color.White, phoneTop + Offset(4f, 6f), Size(34f, 58f), CornerRadius(5f))
        sparkAt(Offset(119f, 245f + bob), 9f, accent)
    }
    drawCircle(skin, 11f, Offset(132f, 262f + bob))
}

private fun DrawScope.waveArm(skin: Color, shirt: Color, wave: Float, bob: Float) {
    val shoulder = Offset(204f, 196f + bob)
    val angle = 22f + wave * 16f
    rotate(angle, shoulder) {
        arm(skin, shirt, shoulder, shoulder + Offset(10f, -46f), shoulder + Offset(14f, -86f))
        // Fingers
        val hand = shoulder + Offset(14f, -86f)
        for (i in -2..1) drawLine(skin, hand, hand + Offset(i * 5f, -14f), 5f, StrokeCap.Round)
    }
}

private fun DrawScope.holdTablet(skin: Color, accent: Color, bob: Float, breathe: Float) {
    val top = Offset(86f, 196f + bob)
    drawRoundRect(Color(0xFF141413), top, Size(128f, 84f), CornerRadius(12f))
    drawRoundRect(Color(0xFF1F2B38), top + Offset(6f, 6f), Size(116f, 72f), CornerRadius(7f))
    // Play triangle pulsing gently
    val c = top + Offset(64f, 42f)
    val r = 13f + breathe * 2f
    val tri = Path().apply {
        moveTo(c.x - r * 0.6f, c.y - r)
        lineTo(c.x + r, c.y)
        lineTo(c.x - r * 0.6f, c.y + r)
        close()
    }
    drawCircle(Color.White.copy(alpha = 0.15f), r * 1.8f, c)
    drawPath(tri, Color.White)
    drawRoundRect(accent, top + Offset(14f, 68f), Size(60f, 4f), CornerRadius(2f))
    drawCircle(skin, 11f, Offset(86f, 246f + bob))
    drawCircle(skin, 11f, Offset(214f, 246f + bob))
}

private fun DrawScope.cheerArms(skin: Color, shirt: Color, wave: Float, bob: Float) {
    val l = Offset(96f, 196f + bob)
    val r = Offset(204f, 196f + bob)
    rotate(-24f - wave * 8f, l) { arm(skin, shirt, l, l + Offset(-12f, -44f), l + Offset(-14f, -84f)) }
    rotate(24f + wave * 8f, r) { arm(skin, shirt, r, r + Offset(12f, -44f), r + Offset(14f, -84f)) }
}

private fun DrawScope.sparkAt(c: Offset, r: Float, color: Color) {
    for (i in 0 until 12) {
        val a = Math.toRadians((i * 30 - 90).toDouble())
        val len = if (i % 2 == 0) r else r * 0.65f
        drawLine(color, c + Offset((r * 0.3f * cos(a)).toFloat(), (r * 0.3f * sin(a)).toFloat()), c + Offset((len * cos(a)).toFloat(), (len * sin(a)).toFloat()), r * 0.18f, StrokeCap.Round)
    }
}

/** Little cards drifting around the character. */
private fun DrawScope.floaters(orbit: Float, accent: Color, pose: Pose) {
    val items = when (pose) {
        Pose.WAVE -> listOf(Color(0xFFFCE7DA), Color(0xFFEAE4FB), Color(0xFFDDF3E6))
        Pose.WATCH -> listOf(Color(0xFFFBF1D2), Color(0xFFFBE3E8), Color(0xFFDDEFFB))
        Pose.CHEER -> listOf(Color(0xFFDDF3E6), Color(0xFFFBF1D2), Color(0xFFEAE4FB))
    }
    val spots = listOf(Offset(56f, 150f), Offset(250f, 120f), Offset(244f, 206f))
    items.forEachIndexed { i, color ->
        val dy = sin(orbit + i * 2.1f) * 8f
        val dx = cos(orbit + i * 1.7f) * 4f
        val p = spots[i] + Offset(dx, dy)
        rotate(if (i % 2 == 0) -8f else 8f, p) {
            drawRoundRect(color, p - Offset(24f, 20f), Size(48f, 40f), CornerRadius(10f))
            when (i) {
                0 -> { // photo
                    drawCircle(Color(0xFFF6B48F), 6f, p + Offset(8f, -6f))
                    val hill = Path().apply {
                        moveTo(p.x - 20f, p.y + 14f)
                        quadraticBezierTo(p.x - 6f, p.y - 2f, p.x + 6f, p.y + 10f)
                        lineTo(p.x + 20f, p.y + 14f)
                        close()
                    }
                    drawPath(hill, Color(0xFFE99A74))
                }
                1 -> { // zipped archive
                    for (k in 0 until 4) drawRect(Color(0xFF7B68D9), Offset(p.x - 3f + (if (k % 2 == 0) -3f else 3f), p.y - 14f + k * 7f), Size(6f, 4f))
                }
                else -> { // play
                    val tri = Path().apply {
                        moveTo(p.x - 6f, p.y - 9f)
                        lineTo(p.x + 9f, p.y)
                        lineTo(p.x - 6f, p.y + 9f)
                        close()
                    }
                    drawPath(tri, accent)
                }
            }
        }
    }
}
