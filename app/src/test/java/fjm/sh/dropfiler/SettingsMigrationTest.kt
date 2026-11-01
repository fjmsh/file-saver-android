package fjm.sh.dropfiler

import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hchen.himiuix.preference.MiuixDropDownPreference
import com.hchen.himiuix.preference.MiuixSwitchPreference
import fjm.sh.dropfiler.TestSupport.context
import fjm.sh.dropfiler.TestSupport.prefs
import fjm.sh.dropfiler.activity.SettingsActivity
import fjm.sh.dropfiler.util.PreferenceHelper
import fjm.sh.dropfiler.util.SettingsDataStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Existing user settings must survive the move to Miuix XML preferences.
 */
@RunWith(AndroidJUnit4::class)
class SettingsMigrationTest {

	private val fileExistsValues get() = context.resources.getStringArray(R.array.pref_list_values_file_exists)

	@Before
	fun setUp() {
		prefs.edit().clear().commit()
	}

	/** Settings as written by version 5.2.0 (ListPreference values + SwitchPreference booleans). */
	private fun writeLegacySettings() {
		prefs.edit()
			.putString("file_exists", "Postfix")
			.putString("log_to_user", "Debug")
			.putBoolean("location_select", true)
			.putBoolean("register_file", true)
			.putBoolean("lenient_regex", true)
			.putBoolean("follow_redirects", false)
			.putBoolean("force_saving", true)
			// Settings of removed features (support pop up, "Save Imgur images"): ignored,
			// but left untouched
			.putBoolean("special_imgur", true)
			.putBoolean("hide_support", true)
			.commit()
	}

	private fun settingsFragment(activity: FragmentActivity): PreferenceFragmentCompat =
		activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as PreferenceFragmentCompat

	@Test
	fun dataStore_mapsBetweenIndexAndStoredValue() {
		val store = SettingsDataStore(prefs, mapOf("file_exists" to fileExistsValues))

		prefs.edit().putString("file_exists", "Nothing").commit()
		assertEquals("1", store.getString("file_exists", "Overwrite"))

		store.putString("file_exists", "2")
		assertEquals("Postfix", prefs.getString("file_exists", null))

		// Out of range indexes are ignored rather than corrupting the setting
		store.putString("file_exists", "9")
		assertEquals("Postfix", prefs.getString("file_exists", null))
	}

	@Test
	fun dataStore_usesXmlDefaultWithoutWriting() {
		val store = SettingsDataStore(prefs, mapOf("file_exists" to fileExistsValues))
		assertEquals("0", store.getString("file_exists", "Overwrite"))
		assertNull("defaults are not persisted", prefs.getString("file_exists", null))
	}

	@Test
	fun dataStore_passesOtherKeysThrough() {
		val store = SettingsDataStore(prefs, emptyMap())
		store.putBoolean("register_file", true)
		store.putString("other", "value")
		assertTrue(prefs.getBoolean("register_file", false))
		assertEquals("value", store.getString("other", null))
	}

	@Test
	@Config(sdk = [23, 26, 28, 29, 31, 34, 36])
	fun openingSettings_keepsLegacyValues() {
		writeLegacySettings()
		val before = prefs.all.toMap()

		val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
		TestSupport.idle()
		val fragment = settingsFragment(activity)

		val fileExists = fragment.findPreference<MiuixDropDownPreference>("file_exists")!!
		assertEquals("2", fileExists.value)
		assertEquals(context.resources.getStringArray(R.array.pref_list_description_file_exists)[2], fileExists.summary)
		assertEquals("4", fragment.findPreference<MiuixDropDownPreference>("log_to_user")!!.value)
		assertTrue(fragment.findPreference<MiuixSwitchPreference>("location_select")!!.isChecked)
		assertFalse(fragment.findPreference<MiuixSwitchPreference>("follow_redirects")!!.isChecked)
		assertNull("support pop up setting removed", fragment.findPreference<MiuixSwitchPreference>("hide_support"))
		assertNull("\"Save Imgur images\" removed", fragment.findPreference<MiuixSwitchPreference>("special_imgur"))
		val links = fragment.findPreference<androidx.preference.PreferenceGroup>("category_links")!!
		assertEquals(listOf("follow_redirects"), (0 until links.preferenceCount).map { links.getPreference(it).key })

		assertEquals("showing settings must not rewrite stored values", before, prefs.all.toMap())

		PreferenceHelper(context).loadPreferences()
		assertEquals(2, PreferenceHelper.saveStrategy)
		assertEquals(4, PreferenceHelper.logLevel)
		assertTrue(PreferenceHelper.locationSelectEnabled)
		assertTrue(PreferenceHelper.registerMediaServer)
		assertTrue(PreferenceHelper.useLenientRegex)
		assertFalse(PreferenceHelper.followRedirects)
		assertTrue(PreferenceHelper.forceSaving)
	}

	@Test
	fun freshInstall_usesOriginalDefaults() {
		val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
		TestSupport.idle()
		val fragment = settingsFragment(activity)

		assertEquals("0", fragment.findPreference<MiuixDropDownPreference>("file_exists")!!.value)
		assertEquals("0", fragment.findPreference<MiuixDropDownPreference>("log_to_user")!!.value)
		assertTrue(fragment.findPreference<MiuixSwitchPreference>("follow_redirects")!!.isChecked)
		assertFalse(fragment.findPreference<MiuixSwitchPreference>("location_select")!!.isChecked)
		assertTrue("nothing written just by opening settings", prefs.all.isEmpty())

		PreferenceHelper(context).loadPreferences()
		assertEquals(0, PreferenceHelper.saveStrategy)
		assertTrue(PreferenceHelper.followRedirects)
	}

	@Test
	@Config(sdk = [28])
	fun dynamicColors_disabledBeforeAndroid12() {
		val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
		TestSupport.idle()
		val dynamic = settingsFragment(activity).findPreference<MiuixSwitchPreference>("dynamic_colors")!!
		assertFalse(dynamic.isEnabled)
	}

	@Test
	@Config(sdk = [31, 36])
	fun dynamicColors_availableOnAndroid12() {
		val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
		TestSupport.idle()
		val dynamic = settingsFragment(activity).findPreference<MiuixSwitchPreference>("dynamic_colors")!!
		assertTrue(dynamic.isEnabled)
	}
}
