package app.realme.unlink.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.realme.unlink.R

private val GoogleSansFlex = FontFamily(Font(R.font.google_sans_flex))

private val ExpressiveDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9ECEFF),
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF00487F),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBCC7DB),
    onSecondary = Color(0xFF273141),
    secondaryContainer = Color(0xFF3E4758),
    onSecondaryContainer = Color(0xFFD8E3F8),
    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2947),
    tertiaryContainer = Color(0xFF523F5E),
    onTertiaryContainer = Color(0xFFF2DAFF),
    surfaceContainerLow = Color(0xFF131B25),
    surfaceContainer = Color(0xFF1B232E),
    surfaceContainerHigh = Color(0xFF252E39),
    surfaceContainerHighest = Color(0xFF303945),
)

private val ExpressiveLightColorScheme = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),
    surfaceContainerLow = Color(0xFFF3F4FA),
    surfaceContainer = Color(0xFFEDEEF4),
    surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E8),
)

private val UnlinkTypography: Typography
    @Composable
    get() {
        val base = Typography()
        fun TextStyle.gs(weight: FontWeight = fontWeight ?: FontWeight.Normal): TextStyle =
            copy(fontFamily = GoogleSansFlex, fontWeight = weight)

        return base.copy(
            displayLarge = base.displayLarge.gs(FontWeight.SemiBold),
            displayMedium = base.displayMedium.gs(FontWeight.SemiBold),
            displaySmall = base.displaySmall.gs(FontWeight.SemiBold),
            headlineLarge = base.headlineLarge.gs(FontWeight.Bold),
            headlineMedium = base.headlineMedium.gs(FontWeight.SemiBold),
            headlineSmall = base.headlineSmall.gs(FontWeight.Medium),
            titleLarge = base.titleLarge.gs(FontWeight.SemiBold),
            titleMedium = base.titleMedium.gs(FontWeight.SemiBold),
            titleSmall = base.titleSmall.gs(FontWeight.Medium),
            bodyLarge = base.bodyLarge.gs(),
            bodyMedium = base.bodyMedium.gs(),
            bodySmall = base.bodySmall.gs(),
            labelLarge = base.labelLarge.gs(FontWeight.SemiBold),
            labelMedium = base.labelMedium.gs(FontWeight.Medium),
            labelSmall = base.labelSmall.gs(FontWeight.Medium),
        )
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UnlinkTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark ->
            dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        dark -> ExpressiveDarkColorScheme
        else -> ExpressiveLightColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        typography = UnlinkTypography,
        content = content,
    )
}
