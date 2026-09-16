package app.realme.unlink.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.realme.unlink.R

/** Bundled from Google Fonts; see res/raw/google_sans_flex_ofl.txt. */
private val GoogleSansFlex = FontFamily(
    Font(R.font.google_sans_flex, weight = FontWeight.Normal),
)

/** Keep the bundled license discoverable in the packaged resources. */
@Suppress("unused")
private val GoogleSansFlexLicense = R.raw.google_sans_flex_ofl

private val UnlinkTypography: Typography
    @Composable
    get() {
        val base = Typography()
        fun TextStyle.googleSans(weight: FontWeight = fontWeight ?: FontWeight.Normal): TextStyle =
            copy(fontFamily = GoogleSansFlex, fontWeight = weight)

        return base.copy(
            displayLarge = base.displayLarge.googleSans(FontWeight.SemiBold),
            displayMedium = base.displayMedium.googleSans(FontWeight.SemiBold),
            displaySmall = base.displaySmall.googleSans(FontWeight.SemiBold),
            headlineLarge = base.headlineLarge.googleSans(FontWeight.Bold),
            headlineMedium = base.headlineMedium.googleSans(FontWeight.SemiBold),
            headlineSmall = base.headlineSmall.googleSans(FontWeight.Medium),
            titleLarge = base.titleLarge.googleSans(FontWeight.SemiBold),
            titleMedium = base.titleMedium.googleSans(FontWeight.SemiBold),
            titleSmall = base.titleSmall.googleSans(FontWeight.Medium),
            bodyLarge = base.bodyLarge.googleSans(),
            bodyMedium = base.bodyMedium.googleSans(),
            bodySmall = base.bodySmall.googleSans(),
            labelLarge = base.labelLarge.googleSans(FontWeight.SemiBold),
            labelMedium = base.labelMedium.googleSans(FontWeight.Medium),
            labelSmall = base.labelSmall.googleSans(FontWeight.Medium),
        )
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UnlinkTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (dark) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        typography = UnlinkTypography,
        content = content,
    )
}
