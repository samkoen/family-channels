package com.familychannels.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familychannels.ui.theme.Coral
import com.familychannels.ui.theme.CoralSoft
import com.familychannels.ui.theme.Danger
import com.familychannels.ui.theme.Ink
import com.familychannels.ui.theme.Mist
import com.familychannels.ui.theme.MistDeep
import com.familychannels.ui.theme.SurfaceWhite
import com.familychannels.ui.theme.Teal
import com.familychannels.ui.theme.TealBright
import com.familychannels.ui.theme.TealDark
import com.familychannels.ui.theme.TealSoft

private val CardShape = RoundedCornerShape(20.dp)
private val ButtonShape = RoundedCornerShape(16.dp)

@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF7FBFC),
                        Mist,
                        Color(0xFFE8F2F4),
                        MistDeep,
                    ),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x5520B2B8), Color.Transparent),
                    center = Offset(w * 0.12f, h * 0.08f),
                    radius = w * 0.55f,
                ),
                center = Offset(w * 0.12f, h * 0.08f),
                radius = w * 0.55f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x33E85D4C), Color.Transparent),
                    center = Offset(w * 0.92f, h * 0.18f),
                    radius = w * 0.42f,
                ),
                center = Offset(w * 0.92f, h * 0.18f),
                radius = w * 0.42f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x3314A3A8), Color.Transparent),
                    center = Offset(w * 0.75f, h * 0.88f),
                    radius = w * 0.5f,
                ),
                center = Offset(w * 0.75f, h * 0.88f),
                radius = w * 0.5f,
            )
        }
        content()
    }
}

@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun BrandMark(size: Dp = 56.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(10.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(TealBright, Teal, TealDark),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size * 0.58f)) {
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.12f, h * 0.08f)
                quadraticBezierTo(w * 0.5f, h * -0.02f, w * 0.88f, h * 0.08f)
                lineTo(w * 0.88f, h * 0.48f)
                quadraticBezierTo(w * 0.5f, h * 1.05f, w * 0.12f, h * 0.48f)
                close()
            }
            drawPath(path, Color.White.copy(alpha = 0.95f))
            val play = Path().apply {
                moveTo(w * 0.38f, h * 0.28f)
                lineTo(w * 0.38f, h * 0.62f)
                lineTo(w * 0.68f, h * 0.45f)
                close()
            }
            drawPath(play, TealDark)
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Teal,
            contentColor = Color.White,
            disabledContainerColor = Teal.copy(alpha = 0.35f),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 1.dp,
        ),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val shape = CardShape
    val cardMod = modifier
        .fillMaxWidth()
        .shadow(
            elevation = 8.dp,
            shape = shape,
            clip = false,
            ambientColor = Teal.copy(alpha = 0.12f),
            spotColor = Teal.copy(alpha = 0.18f),
        )
    val body: @Composable () -> Unit = {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = cardMod,
            color = SurfaceWhite.copy(alpha = 0.94f),
            shape = shape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.85f)),
            content = body,
        )
    } else {
        Surface(
            modifier = cardMod,
            color = SurfaceWhite.copy(alpha = 0.94f),
            shape = shape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.85f)),
            content = body,
        )
    }
}

@Composable
fun LanguageChip(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = Color.White.copy(alpha = 0.65f),
            contentColor = TealDark,
        ),
        border = BorderStroke(1.dp, TealSoft),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(color = TealDark))
    }
}

@Composable
fun ProfileAvatar(
    name: String,
    colorHex: String,
    size: Dp = 48.dp,
) {
    val bg = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
        .getOrDefault(Teal)
    Box(
        modifier = Modifier
            .size(size)
            .shadow(6.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(bg, bg.copy(alpha = 0.78f)),
                ),
            )
            .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Bold,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun QuotaBar(
    remaining: Int,
    limit: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = if (limit <= 0) 0f else (remaining.toFloat() / limit).coerceIn(0f, 1f)
    val fill = when {
        fraction > 0.4f -> Brush.horizontalGradient(listOf(TealBright, Teal))
        fraction > 0.15f -> Brush.horizontalGradient(listOf(Color(0xFFF0A202), Coral))
        else -> Brush.horizontalGradient(listOf(Coral, Danger))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(TealSoft),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(fill),
        )
    }
}

@Composable
fun QuotaBadge(
    label: String,
    remaining: Int,
    limit: Int,
    exhausted: Boolean,
    modifier: Modifier = Modifier,
) {
    SoftCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (exhausted) Danger else Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (exhausted) CoralSoft else TealSoft)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        if (exhausted) "0%" else "${((remaining.toFloat() / limit.coerceAtLeast(1)) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (exhausted) Coral else TealDark,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
            if (!exhausted && limit > 0) {
                QuotaBar(remaining = remaining, limit = limit)
            }
        }
    }
}

@Composable
fun PlayGlyph(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier.size(22.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.32f, size.height * 0.18f)
            lineTo(size.width * 0.32f, size.height * 0.82f)
            lineTo(size.width * 0.82f, size.height * 0.5f)
            close()
        }
        drawPath(path, tint)
    }
}

@Composable
fun ChevronGlyph(modifier: Modifier = Modifier, tint: Color = Teal) {
    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = Stroke(width = 3.2f, cap = StrokeCap.Round)
        drawLine(
            color = tint,
            start = Offset(size.width * 0.35f, size.height * 0.22f),
            end = Offset(size.width * 0.68f, size.height * 0.5f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.68f, size.height * 0.5f),
            end = Offset(size.width * 0.35f, size.height * 0.78f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun LoadingPanel(
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    SoftCard(
        modifier = modifier.padding(top = 12.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(TealSoft.copy(alpha = alpha * 0.5f)),
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(34.dp),
                    color = Teal,
                    trackColor = TealSoft,
                    strokeWidth = 3.5.dp,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            if (!hint.isNullOrBlank()) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFFFF1F0),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFFFC9C5)),
    ) {
        Text(
            message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium.copy(color = Danger),
        )
    }
}
