package app.abot.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val AbotPaper = Color(0xFFFFFFFF)
internal val AbotInk = Color(0xFF062456)
internal val AbotNavy = AbotInk
internal val AbotSky = Color(0xFF8EDCFA)
internal val AbotBlue = Color(0xFF4BBDEB)
internal val AbotSoft = Color(0xFFEEF0F2)
internal val AbotCanvas = Color(0xFFF7F8FA)
internal val AbotLine = Color(0xFFD9E1EA)
internal val AbotMuted = Color(0xFF5B6B82)
internal val AbotDanger = Color(0xFFB42318)
internal val AbotSuccess = Color(0xFF167A58)

internal val AbotCardShape = RoundedCornerShape(22.dp)
internal val AbotButtonShape = RoundedCornerShape(50)
@OptIn(ExperimentalTextApi::class)
private val Manrope = FontFamily(
    Font(R.font.manrope_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.manrope_variable, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

private val AbotColorScheme = lightColorScheme(
    primary = AbotNavy,
    onPrimary = AbotPaper,
    secondary = AbotBlue,
    onSecondary = AbotNavy,
    background = AbotPaper,
    onBackground = AbotNavy,
    surface = AbotPaper,
    onSurface = AbotNavy,
    surfaceVariant = AbotSoft,
    onSurfaceVariant = AbotMuted,
    outline = AbotLine,
    error = AbotDanger,
    onError = AbotPaper,
)

private val AbotTypography = Typography(
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp, lineHeight = 46.sp, letterSpacing = (-1.7).sp),
    headlineLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 37.sp, letterSpacing = (-1.1).sp),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 28.sp, letterSpacing = (-0.55).sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontSize = 11.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 1.1.sp),
)

@Composable
internal fun AbotTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AbotColorScheme, typography = AbotTypography, content = content)
}

@Composable
internal fun AbotBrandMark(modifier: Modifier = Modifier, reversed: Boolean = false) {
    Canvas(modifier = modifier.size(30.dp)) {
        val unit = size.minDimension / 12f
        val dark = if (reversed) AbotPaper else AbotNavy
        drawPath(Path().apply {
            moveTo(0f, 0f); lineTo(5.65f * unit, 0f); lineTo(5.65f * unit, 2.35f * unit)
            lineTo(2.35f * unit, 2.35f * unit); lineTo(2.35f * unit, 9.65f * unit)
            lineTo(5.65f * unit, 9.65f * unit); lineTo(5.65f * unit, 12f * unit)
            lineTo(0f, 12f * unit); close()
        }, dark)
        drawPath(Path().apply {
            moveTo(12f * unit, 0f); lineTo(6.35f * unit, 0f); lineTo(6.35f * unit, 2.35f * unit)
            lineTo(9.65f * unit, 2.35f * unit); lineTo(9.65f * unit, 9.65f * unit)
            lineTo(6.35f * unit, 9.65f * unit); lineTo(6.35f * unit, 12f * unit)
            lineTo(12f * unit, 12f * unit); close()
        }, AbotBlue)
        drawRoundRect(dark, Offset(4.85f * unit, 4.85f * unit), Size(2.3f * unit, 2.3f * unit), androidx.compose.ui.geometry.CornerRadius(.35f * unit))
        drawCircle(if (reversed) AbotNavy else AbotPaper, .5f * unit, Offset(6f * unit, 6f * unit))
    }
}

@Composable
internal fun AbotStatusPill(label: String, connected: Boolean, modifier: Modifier = Modifier, dark: Boolean = false) {
    Surface(modifier = modifier, shape = CircleShape, color = if (dark) AbotPaper.copy(alpha = .12f) else AbotSoft) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = if (connected) AbotBlue else Color(0xFF9AA7B8), modifier = Modifier.size(7.dp)) {}
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (dark) AbotPaper else AbotNavy)
        }
    }
}

@Composable
internal fun AbotSectionLabel(text: String, modifier: Modifier = Modifier, color: Color = AbotMuted) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
internal fun AbotPanel(modifier: Modifier = Modifier, color: Color = AbotSoft, content: @Composable () -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), color = color, shape = AbotCardShape, content = content)
}

@Composable
internal fun AbotTransferArtwork(modifier: Modifier = Modifier, reversed: Boolean = false) {
    Canvas(modifier = modifier.graphicsLayer(scaleX = if (reversed) -1f else 1f)) {
        val navy = if (reversed) AbotPaper else AbotNavy
        val blue = if (reversed) AbotSky else AbotBlue
        val w = size.width
        val h = size.height
        drawPath(Path().apply {
            moveTo(w * .08f, h * .58f); lineTo(w * .90f, h * .18f); lineTo(w * .67f, h * .78f)
            lineTo(w * .49f, h * .56f); lineTo(w * .34f, h * .71f); lineTo(w * .37f, h * .50f); close()
        }, blue)
        drawPath(Path().apply {
            moveTo(w * .37f, h * .50f); lineTo(w * .90f, h * .18f); lineTo(w * .49f, h * .56f)
        }, navy, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
        drawLine(navy.copy(alpha = .6f), Offset(w * .02f, h * .82f), Offset(w * .34f, h * .68f), 1.dp.toPx(), StrokeCap.Round)
        drawCircle(navy, 2.dp.toPx(), Offset(w * .11f, h * .30f), style = Stroke(1.dp.toPx()))
    }
}

@Composable
internal fun AbotNavIcon(section: AbotMobileSection, active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) AbotBlue else AbotMuted
    Box(modifier = modifier.size(22.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.minDimension / 22f
            val stroke = Stroke(width = 1.7f * unit, cap = StrokeCap.Round)
            when (section) {
                AbotMobileSection.HOME -> {
                    val path = Path().apply { moveTo(3f * unit, 10f * unit); lineTo(11f * unit, 3f * unit); lineTo(19f * unit, 10f * unit); lineTo(19f * unit, 19f * unit); lineTo(3f * unit, 19f * unit); close() }
                    drawPath(path, color, style = stroke)
                }
                AbotMobileSection.ACTIVITY -> {
                    drawRoundRect(color, Offset(3f * unit, 4f * unit), Size(16f * unit, 15f * unit), androidx.compose.ui.geometry.CornerRadius(3f * unit), style = stroke)
                    drawLine(color, Offset(7f * unit, 9f * unit), Offset(15f * unit, 9f * unit), stroke.width, StrokeCap.Round)
                    drawLine(color, Offset(7f * unit, 14f * unit), Offset(13f * unit, 14f * unit), stroke.width, StrokeCap.Round)
                }
                AbotMobileSection.SETTINGS -> {
                    drawCircle(color, 7f * unit, center, style = stroke)
                    drawCircle(color, 2.3f * unit, center, style = stroke)
                    drawLine(color, Offset(11f * unit, 1f * unit), Offset(11f * unit, 4f * unit), stroke.width, StrokeCap.Round)
                    drawLine(color, Offset(11f * unit, 18f * unit), Offset(11f * unit, 21f * unit), stroke.width, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
internal fun abotPrimaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = AbotNavy,
    contentColor = AbotPaper,
    disabledContainerColor = Color(0xFFD6DCE4),
    disabledContentColor = Color(0xFF7C899B),
)

@Composable
internal fun abotBlueButtonColors() = ButtonDefaults.buttonColors(
    containerColor = AbotSky,
    contentColor = AbotNavy,
    disabledContainerColor = Color(0xFFD9EDF5),
    disabledContentColor = Color(0xFF758494),
)
