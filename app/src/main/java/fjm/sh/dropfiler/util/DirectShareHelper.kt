package fjm.sh.dropfiler.util

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import fjm.sh.dropfiler.R
import fjm.sh.dropfiler.activity.FolderListActivity

/**
 * Direct Share support.
 *
 * Android 6.0 - 9 query [fjm.sh.dropfiler.service.LocationChooserTargetService].
 * ChooserTargetService is deprecated and no longer queried by recent Android versions,
 * so on Android 10+ the saved folders are also published as sharing shortcuts
 * (res/xml/shortcuts.xml). Selecting one launches SaverActivity with
 * [ShortcutManagerCompat.EXTRA_SHORTCUT_ID], which is mapped back to the folder by [folderForShortcut].
 */
object DirectShareHelper {

	const val CATEGORY_SAVE_TO_FOLDER = "fjm.sh.dropfiler.category.SAVE_TO_FOLDER"
	private const val SHORTCUT_PREFIX = "folder:"
	private const val ICON_SIZE_DP = 108

	private fun shortcutId(folder: String) = SHORTCUT_PREFIX + folder

	/**
	 * @return The saved folder for a sharing shortcut id, or null if it is not one of ours.
	 * Also reports the usage so the system can rank the most used folders first.
	 */
	fun folderForShortcut(context: Context, shortcutId: String?): String? {
		val folder = shortcutId?.takeIf { it.startsWith(SHORTCUT_PREFIX) }?.removePrefix(SHORTCUT_PREFIX)
		if (folder != null) {
			ShortcutManagerCompat.reportShortcutUsed(context, shortcutId)
		}
		return folder
	}

	/**
	 * Publishes (replaces) the sharing shortcuts for the given folders.
	 */
	fun publish(context: Context, folders: List<String>) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
			// Older versions use LocationChooserTargetService
			return
		}
		try {
			val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
			val density = context.resources.displayMetrics.density
			val shortcuts = folders.take(max).mapIndexed { rank, folder ->
				val label = folder.ifBlank { context.getString(R.string.folder_list_root_folder) }
				ShortcutInfoCompat.Builder(context, shortcutId(folder))
					.setShortLabel(label.takeLast(SHORT_LABEL_LENGTH))
					.setLongLabel(label)
					.setIcon(IconCompat.createWithBitmap(
						DirectShareIcon.generate(context, folder, (ICON_SIZE_DP * density).toInt())))
					// Shortcuts need an intent; launched from the launcher it opens the app
					.setIntent(Intent(context, FolderListActivity::class.java).setAction(Intent.ACTION_MAIN))
					.setCategories(setOf(CATEGORY_SAVE_TO_FOLDER))
					.setLongLived(true)
					.setRank(rank)
					.build()
			}
			ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
		} catch (e: RuntimeException) {
			// Rate limiting or launcher restrictions must never break saving
			android.util.Log.w(DirectShareHelper::class.java.simpleName, "Unable to publish sharing shortcuts", e)
		}
	}

	private const val SHORT_LABEL_LENGTH = 15
}
