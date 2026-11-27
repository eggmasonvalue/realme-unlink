package app.realme.unlink.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.realme.unlink.R

private fun googleSansFlex(weight: FontWeight): Font = Font(
    resId = R.font.google_sans_flex,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
    ),
)

/** Bundled variable Google Sans Flex from google/fonts; see res/raw/google_sans_flex_ofl.txt. */
private val GoogleSansFlex = FontFamily(
    googleSansFlex(FontWeight.Normal),
    googleSansFlex(FontWeight.Medium),
    googleSansFlex(FontWeight.SemiBold),
    googleSansFlex(FontWeight.Bold),
)

/** Keep the bundled license discoverable in the packaged resources. */
@Suppress("unused")
private val GoogleSansFlexLicense = R.raw.google_sans_flex_ofl

private val UnlinkTypography = Typography(GoogleSansFlex).let { base ->
    base.copy(
        titleMediumEmphasized = base.titleMediumEmphasized.copy(
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        labelLargeEmphasized = base.labelLargeEmphasized.copy(
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

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
