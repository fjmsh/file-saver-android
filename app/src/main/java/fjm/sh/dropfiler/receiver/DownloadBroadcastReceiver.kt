package fjm.sh.dropfiler.receiver

import android.content.BroadcastReceiver
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import fjm.sh.dropfiler.R
import fjm.sh.dropfiler.util.SaveNotifier
import java.io.File

/**
 * A class for monitoring downloads (URLs saved with DownloadManager) and telling the user
 * the result with a toast and a notification.
 */
class DownloadBroadcastReceiver: BroadcastReceiver() {

	override fun onReceive(context: Context, intent: Intent) {
		if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == intent.action) {
			val result = queryResult(context, intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1))
			val names = listOfNotNull(result?.title?.takeIf { it.isNotBlank() })
			if (result == null || result.successful) {
				Toast.makeText(context, R.string.toast_save_successful, Toast.LENGTH_SHORT).show()
				SaveNotifier.notifySaved(context, names, result?.folder)
			} else {
				Toast.makeText(context, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
				SaveNotifier.notifyFailed(context, null, names)
			}
		}
	}

	private class Result(val successful: Boolean, val title: String?, val folder: String?)

	/**
	 * Looks up the finished download. Returns null if it cannot be found (the previous
	 * behaviour of always reporting success is kept in that case).
	 */
	private fun queryResult(context: Context, id: Long): Result? {
		if (id == -1L) return null
		val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return null
		return try {
			manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
				if (!cursor.moveToFirst()) return null
				val status = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS).takeIf { it >= 0 }?.let { cursor.getInt(it) }
				val title = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE).takeIf { it >= 0 }?.let { cursor.getString(it) }
				val localUri = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI).takeIf { it >= 0 }?.let { cursor.getString(it) }
				val folder = localUri?.let { Uri.parse(it).path }?.let { File(it).parent }
				Result(status == DownloadManager.STATUS_SUCCESSFUL, title, folder)
			}
		} catch (e: RuntimeException) {
			null
		}
	}
}
