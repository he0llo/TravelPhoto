package he.travelphoto.llo.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

internal data class ThemePreset(
    val id: String,
    val label: String,
    val seed: Color,
    val palettes: ThemePalettes,
) {
    fun previewPrimary(dark: Boolean): Color =
        palettes.primary.tone(if (dark) 80 else 40)

    fun previewSecondary(dark: Boolean): Color =
        palettes.secondary.tone(if (dark) 80 else 40)

    fun previewContainer(dark: Boolean): Color =
        palettes.primary.tone(if (dark) 30 else 90)
}

internal val ThemePresets: List<ThemePreset> = listOf(
    ThemePreset("blue", "远行蓝", Color(0xFF00639B), SeedPalettes.blue),
    ThemePreset("green", "松林绿", Color(0xFF2D6A4F), SeedPalettes.green),
    ThemePreset("orange", "落日橙", Color(0xFFB45309), SeedPalettes.orange),
    ThemePreset("pink", "樱花粉", Color(0xFFB3446C), SeedPalettes.pink),
    ThemePreset("purple", "暮光紫", Color(0xFF6750A4), SeedPalettes.purple),
    ThemePreset("teal", "海岸青", Color(0xFF00696D), SeedPalettes.teal),
    ThemePreset("sand", "沙漠金", Color(0xFF8A6A2F), SeedPalettes.sand),
    ThemePreset("red", "火山红", Color(0xFFB3261E), SeedPalettes.red),
)

internal class ThemeState internal constructor(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var themeId by mutableStateOf(
        prefs.getString(KEY_THEME_ID, ThemePresets.first().id)
            ?.takeIf { saved -> ThemePresets.any { it.id == saved } }
            ?: ThemePresets.first().id,
    )
        private set

    var mode by mutableStateOf(
        prefs.getString(KEY_MODE, ThemeMode.SYSTEM.name)
            ?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } }
            ?: ThemeMode.SYSTEM,
    )
        private set

    val preset: ThemePreset
        get() = ThemePresets.firstOrNull { it.id == themeId } ?: ThemePresets.first()

    fun selectPreset(id: String) {
        if (ThemePresets.none { it.id == id }) return
        themeId = id
        prefs.edit().putString(KEY_THEME_ID, id).apply()
    }

    fun selectMode(value: ThemeMode) {
        mode = value
        prefs.edit().putString(KEY_MODE, value.name).apply()
    }

    private companion object {
        const val PREFS = "travelphoto_appearance"
        const val KEY_THEME_ID = "theme_id"
        const val KEY_MODE = "theme_mode"
    }
}

@Composable
internal fun rememberThemeState(): ThemeState {
    val context = LocalContext.current.applicationContext
    return remember(context) { ThemeState(context) }
}

@Composable
internal fun TravelPhotoTheme(themeState: ThemeState, content: @Composable () -> Unit) {
    val dark = when (themeState.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = remember(themeState.themeId, dark) {
        colorSchemeFor(themeState.preset.palettes, SeedPalettes.error, dark)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = TravelTypography,
        content = content,
    )
}

private fun colorSchemeFor(p: ThemePalettes, e: ThemePalettes, dark: Boolean): ColorScheme {
    val base = if (dark) {
        darkColorScheme(
            primary = p.primary.tone(80),
            onPrimary = p.primary.tone(20),
            primaryContainer = p.primary.tone(30),
            onPrimaryContainer = p.primary.tone(90),
            inversePrimary = p.primary.tone(40),
            secondary = p.secondary.tone(80),
            onSecondary = p.secondary.tone(20),
            secondaryContainer = p.secondary.tone(30),
            onSecondaryContainer = p.secondary.tone(90),
            tertiary = p.tertiary.tone(80),
            onTertiary = p.tertiary.tone(20),
            tertiaryContainer = p.tertiary.tone(30),
            onTertiaryContainer = p.tertiary.tone(90),
            background = p.neutral.tone(6),
            onBackground = p.neutral.tone(90),
            surface = p.neutral.tone(6),
            onSurface = p.neutral.tone(90),
            surfaceVariant = p.neutralVariant.tone(30),
            onSurfaceVariant = p.neutralVariant.tone(80),
            surfaceTint = p.primary.tone(80),
            inverseSurface = p.neutral.tone(90),
            inverseOnSurface = p.neutral.tone(20),
            error = e.primary.tone(80),
            onError = e.primary.tone(20),
            errorContainer = e.primary.tone(30),
            onErrorContainer = e.primary.tone(90),
            outline = p.neutralVariant.tone(60),
            outlineVariant = p.neutralVariant.tone(30),
            scrim = Color.Black,
            surfaceBright = p.neutral.tone(24),
            surfaceDim = p.neutral.tone(6),
            surfaceContainerLowest = p.neutral.tone(4),
            surfaceContainerLow = p.neutral.tone(10),
            surfaceContainer = p.neutral.tone(12),
            surfaceContainerHigh = p.neutral.tone(17),
            surfaceContainerHighest = p.neutral.tone(22),
        )
    } else {
        lightColorScheme(
            primary = p.primary.tone(40),
            onPrimary = p.primary.tone(100),
            primaryContainer = p.primary.tone(90),
            onPrimaryContainer = p.primary.tone(10),
            inversePrimary = p.primary.tone(80),
            secondary = p.secondary.tone(40),
            onSecondary = p.secondary.tone(100),
            secondaryContainer = p.secondary.tone(90),
            onSecondaryContainer = p.secondary.tone(10),
            tertiary = p.tertiary.tone(40),
            onTertiary = p.tertiary.tone(100),
            tertiaryContainer = p.tertiary.tone(90),
            onTertiaryContainer = p.tertiary.tone(10),
            background = p.neutral.tone(98),
            onBackground = p.neutral.tone(10),
            surface = p.neutral.tone(98),
            onSurface = p.neutral.tone(10),
            surfaceVariant = p.neutralVariant.tone(90),
            onSurfaceVariant = p.neutralVariant.tone(30),
            surfaceTint = p.primary.tone(40),
            inverseSurface = p.neutral.tone(20),
            inverseOnSurface = p.neutral.tone(95),
            error = e.primary.tone(40),
            onError = e.primary.tone(100),
            errorContainer = e.primary.tone(90),
            onErrorContainer = e.primary.tone(10),
            outline = p.neutralVariant.tone(50),
            outlineVariant = p.neutralVariant.tone(80),
            scrim = Color.Black,
            surfaceBright = p.neutral.tone(98),
            surfaceDim = p.neutral.tone(87),
            surfaceContainerLowest = p.neutral.tone(100),
            surfaceContainerLow = p.neutral.tone(96),
            surfaceContainer = p.neutral.tone(94),
            surfaceContainerHigh = p.neutral.tone(92),
            surfaceContainerHighest = p.neutral.tone(90),
        )
    }

    return base.copy(
        primaryFixed = p.primary.tone(90),
        primaryFixedDim = p.primary.tone(80),
        onPrimaryFixed = p.primary.tone(10),
        onPrimaryFixedVariant = p.primary.tone(30),
        secondaryFixed = p.secondary.tone(90),
        secondaryFixedDim = p.secondary.tone(80),
        onSecondaryFixed = p.secondary.tone(10),
        onSecondaryFixedVariant = p.secondary.tone(30),
        tertiaryFixed = p.tertiary.tone(90),
        tertiaryFixedDim = p.tertiary.tone(80),
        onTertiaryFixed = p.tertiary.tone(10),
        onTertiaryFixedVariant = p.tertiary.tone(30),
    )
}

private val TravelTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        ),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
        labelLarge = base.labelLarge.copy(letterSpacing = 0.2.sp),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
