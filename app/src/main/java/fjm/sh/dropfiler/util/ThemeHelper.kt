package fjm.sh.dropfiler.util

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import fjm.sh.dropfiler.R

/**
 * Appearance settings: light / dark / follow system, and optional dynamic (Monet) colors.
 */
object ThemeHelper {

	const val PREF_THEME = "theme_mode"
	const val PREF_DYNAMIC_COLORS = "dynamic_colors"

	private const val THEME_LIGHT = "light"
	private const val THEME_DARK = "dark"

	/**
	 * Dynamic colors need the Android 12 system palette (@android:color/system_accent1_*).
	 */
	val isDynamicColorAvailable: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

	/**
	 * A system-wide dark theme only exists from Android 10.
	 */
	val isSystemDarkThemeAvailable: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

	fun nightModeFor(value: String?): Int = when (value) {
		THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
		THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
		else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
	}

	fun applyNightMode(context: Context) {
		val value = PreferenceManager.getDefaultSharedPreferences(context)
			.getString(PREF_THEME, context.getString(R.string.pref_default_value_theme))
		val mode = nightModeFor(value)
		if (AppCompatDelegate.getDefaultNightMode() != mode) {
			AppCompatDelegate.setDefaultNightMode(mode)
		}
	}

	fun isDynamicColorEnabled(context: Context): Boolean = isDynamicColorAvailable &&
			PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_DYNAMIC_COLORS, false)

	/**
	 * Must be called before the activity inflates any view.
	 */
	fun applyDynamicColors(activity: Activity) {
		if (isDynamicColorEnabled(activity)) {
			activity.theme.applyStyle(R.style.ThemeOverlay_FileSaver_DynamicColors, true)
		}
	}

	/**
	 * Identifies the color configuration an activity was created with, so that activities
	 * in the back stack can be recreated after the user changes it.
	 */
	fun signature(context: Context): String = "dynamic=${isDynamicColorEnabled(context)}"
}
