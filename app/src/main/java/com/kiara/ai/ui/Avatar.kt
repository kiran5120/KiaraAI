package com.kiara.ai.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class AvatarState { Idle, Listening, Thinking, Speaking }

@Composable
fun KiaraAvatar(state: AvatarState, level: Float, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    val a = pal.primary
    val b = pal.secondary
    val t = rememberInfiniteTransition(label = "avatar")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "rot")
    val pulse by t.animateFloat(0.96f, 1.05f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val talk by t.animateFloat(0f, 1f, infiniteRepeatable(tween(260, easing = LinearEasing), RepeatMode.Reverse), label = "talk")
    val speed = if (state == AvatarState.Thinking) 2.5f else 1f
    val boost = when (state) {
        AvatarState.Listening -> 1f + level * 0.12f
        AvatarState.Speaking -> 1.05f
        else -> 1f
    }

    Canvas(modifier.size(280.dp)) {
        val c = center
        val r = size.minDimension / 2f
        val glowR = r * pulse * boost

        // outer glow
        drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.55f), Color.Transparent), c, glowR), glowR)

        // rotating neon rings
        rotate(rot * speed, c) {
            drawCircle(Brush.sweepGradient(listOf(a, b, Color.Transparent, a), c), r * 0.86f, style = Stroke(6.dp.toPx()))
        }
        rotate(-rot * 0.7f * speed, c) {
            drawCircle(
                b.copy(alpha = 0.8f), r * 0.78f,
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 18f)))
            )
        }

        // inner disc
        val u = r * 0.7f
        drawCircle(Brush.radialGradient(listOf(Color(0xFF241446), Color(0xFF0B0820)), c, u), u)

        clipPath(Path().apply { addOval(Rect(c, u)) }) {
            val hair = Brush.verticalGradient(listOf(Color(0xFF3B1E6E), Color(0xFF1A0E3A)), c.y - u, c.y + u)
            val skin = Color(0xFFF1C9B8)

            // long hair behind
            drawOval(hair, Offset(c.x - u * 0.55f, c.y - u * 0.8f), Size(u * 1.1f, u * 1.6f))

            // shoulders
            val sh = Path().apply {
                moveTo(c.x - u * 0.9f, c.y + u)
                cubicTo(c.x - u * 0.85f, c.y + u * 0.5f, c.x - u * 0.35f, c.y + u * 0.42f, c.x - u * 0.12f, c.y + u * 0.38f)
                lineTo(c.x + u * 0.12f, c.y + u * 0.38f)
                cubicTo(c.x + u * 0.35f, c.y + u * 0.42f, c.x + u * 0.85f, c.y + u * 0.5f, c.x + u * 0.9f, c.y + u)
                close()
            }
            drawPath(sh, Brush.verticalGradient(listOf(b.copy(alpha = 0.95f), a.copy(alpha = 0.6f)), c.y + u * 0.4f, c.y + u))
            drawPath(sh, a, style = Stroke(2.dp.toPx()))

            // neck + face
            drawRoundRect(skin.copy(alpha = 0.92f), Offset(c.x - u * 0.1f, c.y + u * 0.15f), Size(u * 0.2f, u * 0.3f), CornerRadius(u * 0.05f))
            drawOval(skin, Offset(c.x - u * 0.33f, c.y - u * 0.52f), Size(u * 0.66f, u * 0.82f))

            // side-swept fringe
            val fringe = Path().apply {
                moveTo(c.x - u * 0.36f, c.y - u * 0.15f)
                cubicTo(c.x - u * 0.40f, c.y - u * 0.75f, c.x + u * 0.40f, c.y - u * 0.78f, c.x + u * 0.36f, c.y - u * 0.2f)
                cubicTo(c.x + u * 0.15f, c.y - u * 0.45f, c.x - u * 0.10f, c.y - u * 0.50f, c.x - u * 0.36f, c.y - u * 0.15f)
                close()
            }
            drawPath(fringe, hair)

            // eyes with glowing irises
            for (s in listOf(-1f, 1f)) {
                val ex = c.x + s * u * 0.14f
                val ey = c.y - u * 0.12f
                drawOval(Color.White, Offset(ex - u * 0.07f, ey - u * 0.04f), Size(u * 0.14f, u * 0.08f))
                drawCircle(a, u * 0.035f, Offset(ex, ey))
                drawCircle(Color.White.copy(alpha = 0.9f), u * 0.012f, Offset(ex + u * 0.01f, ey - u * 0.01f))
                drawLine(Color(0xFF2A1450), Offset(ex - u * 0.08f, ey - u * 0.08f), Offset(ex + u * 0.08f, ey - u * 0.09f), 2.dp.toPx(), StrokeCap.Round)
            }

            // mouth: smile, opens a little while speaking
            val open = if (state == AvatarState.Speaking) talk * u * 0.05f else 0f
            val lip = Path().apply {
                moveTo(c.x - u * 0.09f, c.y + u * 0.12f)
                quadraticBezierTo(c.x, c.y + u * 0.19f + open, c.x + u * 0.09f, c.y + u * 0.12f)
            }
            drawPath(lip, Color(0xFFE0457B), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            if (open > 0f) drawOval(Color(0xFF8A1F4A), Offset(c.x - u * 0.05f, c.y + u * 0.135f), Size(u * 0.10f, open + 2f))
        }

        // thin outline on top of the disc
        drawCircle(a.copy(alpha = 0.7f), u, style = Stroke(2.dp.toPx()))
    }
}

@Composable
fun Waveform(active: Boolean, level: Float, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "phase")
    Canvas(modifier.fillMaxWidth().height(56.dp)) {
        val n = 32
        val gap = size.width / n
        for (i in 0 until n) {
            val wave = 0.4f + 0.6f * abs(sin(phase + i * 0.45f))
            val amp = if (active) (0.2f + 0.8f * level.coerceIn(0f, 1f)) * wave else 0.06f
            val h = size.height * amp
            drawRoundRect(
                lerp(pal.primary, pal.secondary, i / n.toFloat()),
                Offset(i * gap + gap * 0.2f, (size.height - h) / 2f),
                Size(gap * 0.6f, h),
                CornerRadius(gap * 0.3f)
            )
        }
    }
}
