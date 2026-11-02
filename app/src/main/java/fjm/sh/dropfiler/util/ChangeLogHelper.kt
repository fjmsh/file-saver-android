package fjm.sh.dropfiler.util

import android.app.Activity
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.hchen.himiuix.dialog.MiuixAlertDialog
import de.cketti.library.changelog.ChangeLog
import fjm.sh.dropfiler.R
import com.hchen.himiuix.R as MiuixR
import fjm.sh.dropfiler.ui.MaxHeightScrollView

/**
 * Shows the change log in a Miuix dialog.
 *
 * ckChangeLog is still used to parse res/xml/changelog_master.xml and to remember the last
 * version the user has seen, so "What's New" keeps working for upgrading users.
 */
object ChangeLogHelper {

	/**
	 * Show the change log.
	 * Shows the full change log when nothing is in "What's New" log. Shows "What's New" log otherwise.
	 * @param force Force the change log to be displayed, if false only displayed if new content.
	 */
	fun show(activity: Activity, force: Boolean) {
		val cl = ChangeLog(activity)
		if (force || cl.isFirstRun) {
			showDialog(activity, cl, full = cl.getChangeLog(false).isEmpty())
		}
	}

	private fun showDialog(activity: Activity, cl: ChangeLog, full: Boolean) {
		val text = TextView(activity).apply {
			setTextColor(ContextCompat.getColor(activity, MiuixR.color.miuix_dialog_message))
			setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(MiuixR.dimen.miuix_summary))
			setLineSpacing(0f, 1.2f)
			text = buildLog(activity, cl.getChangeLog(full))
		}
		val scroll = MaxHeightScrollView(activity).apply { addView(text) }

		val dialog = MiuixAlertDialog(activity)
			.setTitle(activity.getString(if (full) R.string.changelog_full_title else R.string.changelog_title))
			.setCustomView(scroll)
			// Remember that this version's log has been seen, however the dialog is closed
			.setOnDismissListener { cl.skipLogDialog() }
		if (!full) {
			dialog.setNeutralButton(activity.getString(R.string.changelog_show_full)) { _, _ ->
				showDialog(activity, cl, true)
			}
		}
		dialog.setPositiveButton(activity.getString(R.string.changelog_ok_button), null)
			.show()
	}

	private fun buildLog(activity: Activity, releases: List<ChangeLog.ReleaseItem>): CharSequence {
		val builder = SpannableStringBuilder()
		val gap = activity.resources.getDimensionPixelSize(R.dimen.dialog_padding)
		releases.forEachIndexed { index, release ->
			if (index > 0) builder.append("\n\n")
			val start = builder.length
			builder.append(activity.getString(R.string.credits_version, release.versionName))
			builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			builder.setSpan(RelativeSizeSpan(1.1f), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			release.changes.forEach { change ->
				builder.append("\n")
				val changeStart = builder.length
				builder.append(change)
				builder.setSpan(BulletSpan(gap), changeStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}
		}
		return builder
	}
}
