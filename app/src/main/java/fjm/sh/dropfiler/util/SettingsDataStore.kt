package fjm.sh.dropfiler.util

import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore

/**
 * Bridges Miuix XML preferences and the existing SharedPreferences format.
 *
 * MiuixDropDownPreference persists the *index* of the selected entry ("0", "1", ...),
 * whereas earlier versions stored the entry *value* of a ListPreference (for example
 * "Overwrite" or "None") and the rest of the app reads that value. For the keys listed in
 * [entryValues] this store converts between the two, so existing user settings are read
 * unchanged and new selections are written in the old format. All other keys are passed
 * through to the default SharedPreferences untouched.
 */
class SettingsDataStore(
	private val prefs: SharedPreferences,
	private val entryValues: Map<String, Array<String>>,
) : PreferenceDataStore() {

	override fun getString(key: String, defValue: String?): String? {
		val values = entryValues[key] ?: return prefs.getString(key, defValue)
		val stored = prefs.getString(key, null)
		val index = stored?.let { values.indexOf(it) }?.takeIf { it >= 0 }
			?: defValue?.let { indexOfDefault(values, it) }
			?: return defValue
		return index.toString()
	}

	override fun putString(key: String, value: String?) {
		val values = entryValues[key]
		if (values == null) {
			prefs.edit().putString(key, value).apply()
			return
		}
		val index = value?.toIntOrNull()
		if (index != null && index in values.indices) {
			prefs.edit().putString(key, values[index]).apply()
		}
	}

	/**
	 * The XML default is the entry value (as for the old ListPreference); accept an index too.
	 */
	private fun indexOfDefault(values: Array<String>, defValue: String): Int? =
		values.indexOf(defValue).takeIf { it >= 0 } ?: defValue.toIntOrNull()?.takeIf { it in values.indices }

	override fun getBoolean(key: String, defValue: Boolean): Boolean = prefs.getBoolean(key, defValue)

	override fun putBoolean(key: String, value: Boolean) {
		prefs.edit().putBoolean(key, value).apply()
	}

	override fun getInt(key: String, defValue: Int): Int = prefs.getInt(key, defValue)

	override fun putInt(key: String, value: Int) {
		prefs.edit().putInt(key, value).apply()
	}

	override fun getLong(key: String, defValue: Long): Long = prefs.getLong(key, defValue)

	override fun putLong(key: String, value: Long) {
		prefs.edit().putLong(key, value).apply()
	}

	override fun getFloat(key: String, defValue: Float): Float = prefs.getFloat(key, defValue)

	override fun putFloat(key: String, value: Float) {
		prefs.edit().putFloat(key, value).apply()
	}

	override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
		prefs.getStringSet(key, defValues)

	override fun putStringSet(key: String, values: MutableSet<String>?) {
		prefs.edit().putStringSet(key, values).apply()
	}
}
