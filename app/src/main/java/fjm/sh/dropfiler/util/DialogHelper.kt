package fjm.sh.dropfiler.util

import android.app.Activity
import com.hchen.himiuix.dialog.MiuixAlertDialog
import fjm.sh.dropfiler.R

/**
 * A helper class for managing dialog pop ups. Dialogs use the Miuix XML dialog component.
 */
object DialogHelper {

	/**
	 * Shows a confirmation dialog with Cancel and a confirm button.
	 */
	fun showConfirmDialog(activity: Activity, title: CharSequence, message: CharSequence,
						  confirmText: CharSequence, onConfirm: () -> Unit) {
		MiuixAlertDialog(activity)
			.setTitle(title)
			.setMessage(message)
			.setNegativeButton(activity.getString(R.string.dialog_negative), null)
			.setPositiveButton(confirmText) { _, _ -> onConfirm() }
			.setHapticFeedbackEnabled(true)
			.show()
	}
}
