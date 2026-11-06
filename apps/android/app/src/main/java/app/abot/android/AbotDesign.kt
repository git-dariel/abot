package app.abot.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val AbotPaper = Color(0xFFFFFFFF)
internal val AbotInk = Color(0xFF050505)
internal val AbotBlue = Color(0xFF548CFF)
internal val AbotSoft = Color(0xFFF5F6F8)
internal val AbotLine = Color(0xFFDEDEDE)
internal val AbotMuted = Color(0xFF696969)
internal val AbotDanger = Color(0xFFBA2B2B)

private val AbotColorScheme = lightColorScheme(
    primary = AbotInk,
    onPrimary = AbotPaper,
    secondary = AbotBlue,
    onSecondary = AbotInk,
    background = AbotPaper,
    onBackground = AbotInk,
    surface = AbotPaper,
    onSurface = AbotInk,
    surfaceVariant = AbotSoft,
    onSurfaceVariant = AbotMuted,
    outline = AbotLine,
    error = AbotDanger,
    onError = AbotPaper,
)

private val AbotTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 58.sp,
        lineHeight = 52.sp,
        letterSpacing = (-3.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 38.sp,
        lineHeight = 39.sp,
        letterSpacing = (-1.8).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.9).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.4).sp,
    ),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
    ),
)

@Composable
internal fun AbotTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AbotColorScheme, typography = AbotTypography, content = content)
}

@Composable
internal fun AbotStatusPill(
    label: String,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = AbotPaper,
        border = BorderStroke(1.dp, AbotLine),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = if (connected) AbotBlue else Color(0xFFA8A8A8)) {
                Text(" ", fontSize = 5.sp, modifier = Modifier.padding(3.dp))
            }
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun AbotSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelSmall, color = AbotMuted)
}

@Composable
internal fun AbotPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AbotPaper,
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, AbotInk),
        content = content,
    )
}

@Composable
internal fun abotPrimaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = AbotInk,
    contentColor = AbotPaper,
    disabledContainerColor = Color(0xFFE2E2E2),
    disabledContentColor = Color(0xFF858585),
)

@Composable
internal fun abotBlueButtonColors() = ButtonDefaults.buttonColors(
    containerColor = AbotBlue,
    contentColor = AbotInk,
    disabledContainerColor = Color(0xFFD8E4FF),
    disabledContentColor = Color(0xFF737373),
)
