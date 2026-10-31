package fjm.sh.dropfiler

import android.util.TypedValue
import android.view.accessibility.AccessibilityNodeInfo
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hchen.himiuix.MiuixSwitchView
import com.hchen.himiuix.utils.MiuiSuperBlur
import fjm.sh.dropfiler.TestSupport.context
import fjm.sh.dropfiler.TestSupport.prefs
import fjm.sh.dropfiler.activity.FolderListActivity
import fjm.sh.dropfiler.activity.SettingsActivity
import fjm.sh.dropfiler.util.BlurSupport
import fjm.sh.dropfiler.util.ThemeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class AppearanceTest {

	@Before
	fun setUp() {
		prefs.edit().clear().commit()
		de.cketti.library.changelog.ChangeLog(context).skipLogDialog()
	}

	private fun accentOf(activity: FolderListActivity): Int {
		val value = TypedValue()
		assertTrue(activity.theme.resolveAttribute(R.attr.fsColorAccent, value, true))
		return value.data
	}

	@Test
	fun themePreference_mapsToNightModes() {
		assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeHelper.nightModeFor("light"))
		assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeHelper.nightModeFor("dark"))
		assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeHelper.nightModeFor("system"))
		assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeHelper.nightModeFor(null))
	}

	@Test
	fun themePreference_isApplied() {
		prefs.edit().putString(ThemeHelper.PREF_THEME, "dark").commit()
		ThemeHelper.applyNightMode(context)
		assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
		prefs.edit().putString(ThemeHelper.PREF_THEME, "light").commit()
		ThemeHelper.applyNightMode(context)
		assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())
		AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
	}

	@Test
	@Config(sdk = [31, 34, 36])
	fun dynamicColors_replaceAccentOnAndroid12() {
		val defaultAccent = accentOf(Robolectric.buildActivity(FolderListActivity::class.java).setup().get())
		prefs.edit().putBoolean(ThemeHelper.PREF_DYNAMIC_COLORS, true).commit()
		val dynamic = Robolectric.buildActivity(FolderListActivity::class.java).setup().get()

		assertEquals(dynamic.getColor(android.R.color.system_accent1_600), accentOf(dynamic))
		// The "New folder" button uses system accent 3
		val secondary = TypedValue()
		assertTrue(dynamic.theme.resolveAttribute(R.attr.fsColorSecondaryActionContainer, secondary, true))
		assertEquals(dynamic.getColor(android.R.color.system_accent3_100), secondary.data)
		assertNotEquals(defaultAccent, accentOf(dynamic))
	}

	@Test
	@Config(sdk = [28])
	fun dynamicColors_ignoredBeforeAndroid12() {
		prefs.edit().putBoolean(ThemeHelper.PREF_DYNAMIC_COLORS, true).commit()
		assertFalse(ThemeHelper.isDynamicColorEnabled(context))
		val activity = Robolectric.buildActivity(FolderListActivity::class.java).setup().get()
		assertEquals(activity.getColor(R.color.fs_accent), accentOf(activity))
	}

	@Test
	@Config(sdk = [28, 31, 36])
	fun blur_isNotEnabledWithoutXiaomiSupport() {
		BlurSupport.configure(context)
		assertFalse(BlurSupport.isSupported(context))
		assertFalse(MiuiSuperBlur.isSupportBlur())
	}

	@Test
	@Config(sdk = [23, 28, 36])
	fun miuixSwitchRow_reportsCheckedStateToAccessibility() {
		val themed = ContextThemeWrapper(context, R.style.Theme_FileSaver)
		val view = MiuixSwitchView(themed)
		view.setTitle("Register with media scanner")
		view.setChecked(true)

		val info = AccessibilityNodeInfo.obtain()
		view.onInitializeAccessibilityNodeInfo(info)
		assertTrue(info.isCheckable)
		assertTrue(info.isChecked)
		assertEquals(android.widget.Switch::class.java.name, info.className)
	}

	private fun checkedSwitchColors(root: android.view.View): List<Int> {
		// Lay out the whole preference list so every switch row exists
		root.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
			android.view.View.MeasureSpec.makeMeasureSpec(8000, android.view.View.MeasureSpec.EXACTLY))
		root.layout(0, 0, 1080, 8000)
		TestSupport.idle()
		val found = mutableListOf<Int>()
		fun walk(v: android.view.View) {
			if (v is com.hchen.himiuix.widget.MiuixSwitch && v.isChecked) {
				(v.background as? android.graphics.drawable.GradientDrawable)?.color?.defaultColor?.let(found::add)
			}
			if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
		}
		walk(root)
		return found
	}

	/**
	 * Turning dynamic colors on in Settings recreates the screen; its switches must then use
	 * the dynamic accent, not the Miuix blue cached while the screen had the default colors.
	 */
	@Test
	@Config(sdk = [31, 34, 36])
	fun dynamicColors_enabledInSettings_recolorSettingsScreen() {
		val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
		TestSupport.idle()
		val before = checkedSwitchColors(controller.get().window.decorView)
		assertTrue("a switch is on by default (Follow redirects)", before.isNotEmpty())

		// Toggle the switch like a tap does (Miuix state listener -> persist -> recreate)
		val fragment = controller.get().supportFragmentManager.fragments
			.filterIsInstance<androidx.preference.PreferenceFragmentCompat>().single()
		val switch = fragment.findPreference<com.hchen.himiuix.preference.MiuixSwitchPreference>(ThemeHelper.PREF_DYNAMIC_COLORS)!!
		(switch as com.hchen.himiuix.callback.OnStateChangeListener).onStateChange(true)
		TestSupport.idle()
		assertTrue(prefs.getBoolean(ThemeHelper.PREF_DYNAMIC_COLORS, false))
		val activity = controller.get()
		val accent = TypedValue().also {
			assertTrue(activity.theme.resolveAttribute(R.attr.fsColorAccent, it, true))
		}.data
		val after = checkedSwitchColors(activity.window.decorView)
		assertTrue(after.isNotEmpty())
		assertNotEquals("accent must change", before.first(), accent)
		after.forEach { assertEquals(Integer.toHexString(accent), Integer.toHexString(it)) }
	}

	/**
	 * Dark mode: Miuix defines its accent colors in values-night, which outranks the app's
	 * color-v31 overrides; color-night-v31 must make the switches follow dynamic colors too.
	 */
	@Test
	@Config(sdk = [31, 34, 36], qualifiers = "+night")
	fun dynamicColors_enabledInSettings_recolorSettingsScreen_dark() = dynamicColors_enabledInSettings_recolorSettingsScreen()
}
