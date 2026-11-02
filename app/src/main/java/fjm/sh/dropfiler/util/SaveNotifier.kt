package fjm.sh.dropfiler.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fjm.sh.dropfiler.R
import fjm.sh.dropfiler.activity.FolderListActivity
import java.io.File

/**
 * Posts a notification with the result of a save (success or failure).
 *
 * The app targets API 28, so on Android 13+ the system asks the user for the notification
 * permission when the channel is first created while an activity is visible; until it is
 * allowed nothing is shown. Toasts are still shown either way.
 */
object SaveNotifier {

	const val CHANNEL_ID = "save_results"

	private fun log(context: Context) = DebugLogger(context, SaveNotifier::class.java.simpleName)

	/**
	 * Creates the notification channel (Android 8+). Safe to call repeatedly.
	 */
	fun createChannel(context: Context) {
		val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
			.setName(context.getString(R.string.notification_channel_name))
			.setDescription(context.getString(R.string.notification_channel_description))
			.setSound(null, null)
			.build()
		NotificationManagerCompat.from(context).createNotificationChannel(channel)
	}

	/**
	 * Describes where the files were saved, for the notification text.
	 * @param location Absolute destination folder, or null when the system file picker was used.
	 */
	fun describeLocation(context: Context, location: String?): String = when {
		location == null -> context.getString(R.string.notification_location_picker)
		else -> LocationHelper.removeRoot(location).ifBlank { File.separator }
	}

	/**
	 * @param fileNames Names of the saved files, may be empty when unknown.
	 * @param location See [describeLocation]
	 */
	fun notifySaved(context: Context, fileNames: List<String>, location: String?) {
		val where = describeLocation(context, location)
		val text = when (fileNames.size) {
			0 -> context.getString(R.string.notification_saved_to, where)
			1 -> context.getString(R.string.notification_saved_file, fileNames[0], where)
			else -> context.resources.getQuantityString(R.plurals.notification_saved_files, fileNames.size, fileNames.size, where)
		}
		post(context, context.getString(R.string.notification_saved_title), text, R.drawable.fs_ic_stat_saved)
	}

	/**
	 * @param reason Why the save failed, if known (for example "file already exists").
	 */
	fun notifyFailed(context: Context, reason: CharSequence?, fileNames: List<String>) {
		val detail = reason ?: context.getString(R.string.toast_save_failed)
		val text = if (fileNames.size == 1) context.getString(R.string.notification_failed_file, fileNames[0], detail) else detail
		post(context, context.getString(R.string.notification_failed_title), text, R.drawable.fs_ic_stat_failed)
	}

	private fun post(context: Context, title: CharSequence, text: CharSequence, icon: Int) {
		val manager = NotificationManagerCompat.from(context)
		if (!manager.areNotificationsEnabled()) {
			log(context).d("Notifications are disabled, not posting save result")
			return
		}
		createChannel(context)

		val openApp = PendingIntent.getActivity(context, 0,
			Intent(context, FolderListActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

		val notification = NotificationCompat.Builder(context, CHANNEL_ID)
			.setSmallIcon(icon)
			.setColor(ContextCompat.getColor(context, R.color.fs_accent))
			.setContentTitle(title)
			.setContentText(text)
			.setStyle(NotificationCompat.BigTextStyle().bigText(text))
			.setCategory(NotificationCompat.CATEGORY_STATUS)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.setContentIntent(openApp)
			.setAutoCancel(true)
			.build()

		try {
			// Unique id so the results of several saves are all kept
			manager.notify((System.currentTimeMillis() and 0x7FFFFFFF).toInt(), notification)
		} catch (e: SecurityException) {
			// Notification permission revoked on Android 13+; the toast was already shown
			log(context).w("Not allowed to post notifications: ${e.message}")
		}
	}
}
