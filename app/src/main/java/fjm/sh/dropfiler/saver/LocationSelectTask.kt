package fjm.sh.dropfiler.saver

import android.content.Intent
import android.os.Build
import fjm.sh.dropfiler.activity.SaverActivity
import fjm.sh.dropfiler.util.DebugLogger

/**
 * An async task to handle saving a single text entry
 */
class LocationSelectTask
internal constructor(private val saverActivity: SaverActivity) {

	private lateinit var log: DebugLogger

	fun save(filename: String, mime: String){
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
			// Not available for this API
			saverActivity.failOnActivityResult(saverActivity.requestCodeLocationSelect)
		} else {
			log = DebugLogger(saverActivity, LocationSelectTask::class.java.simpleName)
			val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
			intent.addCategory(Intent.CATEGORY_OPENABLE)
			intent.type = mime
			intent.putExtra(Intent.EXTRA_TITLE, filename)
			saverActivity.startActivityForResult(intent, saverActivity.requestCodeLocationSelect)
		}
	}
}
