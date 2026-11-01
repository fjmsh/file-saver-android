package fjm.sh.dropfiler.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fjm.sh.dropfiler.R
import java.io.File

/**
 * A helper class for managing the stored locations
 */
object LocationHelper {

	private const val FOLDER_LIST_STORE = "FOLDER_STORE"

	/**
	 * The built-in destination, relative to [rootLocation]. It is always available and cannot
	 * be removed, so sharing works before any destination has been added. It is not stored
	 * in FOLDER_STORE, which keeps only the user's destinations (format unchanged).
	 */
	const val DEFAULT_FOLDER = "/DropFiler"

	/** Setting: hide [DEFAULT_FOLDER] while the user has other destinations. */
	const val PREF_HIDE_DEFAULT_FOLDER = "hide_default_folder"

	fun isDefaultFolder(folder: String): Boolean = folder == DEFAULT_FOLDER

	/**
	 * The destinations offered for saving, in display order: [DEFAULT_FOLDER] first, then the
	 * user's destinations. The default folder is left out only when the user chose to hide
	 * it *and* has at least one other destination, so there is always somewhere to save.
	 */
	fun destinations(context: Context, userFolders: List<String> = loadFolderList(context).orEmpty()): List<String> {
		val others = userFolders.filterNot { isDefaultFolder(it) }
		val hide = PreferenceManager.getDefaultSharedPreferences(context)
			.getBoolean(PREF_HIDE_DEFAULT_FOLDER, false)
		return if (hide && others.isNotEmpty()) others else listOf(DEFAULT_FOLDER) + others
	}

	/**
	 * Creates the default folder if it is the given (absolute) destination and is missing,
	 * e.g. on first use or after it was deleted. Other destinations are never created.
	 * @return Whether the destination is an existing folder.
	 */
	fun prepareDestination(absoluteLocation: String): Boolean {
		val folder = File(absoluteLocation)
		if (!folder.isDirectory && absoluteLocation == addRoot(DEFAULT_FOLDER)) {
			folder.mkdirs()
		}
		return folder.isDirectory
	}

	val rootLocation = Environment.getExternalStorageDirectory().absolutePath!!

	/**
	 * Loads the list of folder paths.
	 */
	fun loadFolderList(context: Context): List<String>? {
		val type = object : TypeToken<List<String>>() {}.type
		return Gson().fromJson<List<String>>(JsonFileHelper.getJsonFromFile(context, FOLDER_LIST_STORE), type)
	}

	/**
	 * The [destinations] offered for saving, with Location Select first if enabled.
	 */
	fun loadFolderListWLocationSelect(context: Context): List<String> {
		val locationSelectEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
				PreferenceManager.getDefaultSharedPreferences(context)
					.getBoolean("location_select", false)
		val destinations = destinations(context)
		return if (locationSelectEnabled) {
			listOf(context.resources.getString(R.string.location_select_label)) + destinations
		} else destinations
	}

	/**
	 * Save the list of folder paths.
	 */
	fun saveFolderList(context: Context, folderList: MutableList<String>) {
		if (JsonFileHelper.saveJsonToFile(context, Gson().toJson(folderList), FOLDER_LIST_STORE)){
			// Success. Keep Direct Share (sharing shortcuts) in sync.
			DirectShareHelper.publish(context, destinations(context, folderList))
		} else {
			//TODO Toast an error? Or pass it up
		}
	}

	/**
	 * Remove the root location from the given path.
	 */
	fun removeRoot(location: String): String = location.replace(rootLocation, "")

	/**
	 * Add the root location to the given path, if not already added.
	 */
	fun addRoot(location: String): String = if (location.startsWith(rootLocation))
		location else (rootLocation + location)

	/**
	 * Add the location path if not null and not already added.
	 */
	fun safeAddPath(location: String?, filename: String): String {
		location?.let {
			if (!filename.startsWith(it)){
				return it + File.separatorChar + filename
			}
		}
		return filename
	}

}
