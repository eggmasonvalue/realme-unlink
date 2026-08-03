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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.realme.unlink.R

private val GoogleSansFlex = FontFamily(Font(R.font.google_sans_flex))

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
            headlineLarge = base.headlineLarge.gs(FontWeight.SemiBold),
            headlineMedium = base.headlineMedium.gs(FontWeight.SemiBold),
            headlineSmall = base.headlineSmall.gs(FontWeight.Medium),
            titleLarge = base.titleLarge.gs(FontWeight.SemiBold),
            titleMedium = base.titleMedium.gs(FontWeight.Medium),
            titleSmall = base.titleSmall.gs(FontWeight.Medium),
            bodyLarge = base.bodyLarge.gs(),
            bodyMedium = base.bodyMedium.gs(),
            bodySmall = base.bodySmall.gs(),
            labelLarge = base.labelLarge.gs(FontWeight.Medium),
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
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        typography = UnlinkTypography,
        content = content,
    )
}
