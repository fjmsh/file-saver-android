package fjm.sh.dropfiler

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import fjm.sh.dropfiler.util.LocationHelper
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowEnvironment
import java.io.File

/**
 * Shared helpers for the Robolectric tests.
 */
object TestSupport {

	val context: Context
		get() = ApplicationProvider.getApplicationContext()

	val prefs: SharedPreferences
		get() = PreferenceManager.getDefaultSharedPreferences(context)

	/**
	 * The legacy external storage root used by LocationHelper. LocationHelper caches the
	 * root for the whole test run, so Robolectric's per-test external storage directory is
	 * pointed at the same place.
	 */
	fun root(): File {
		val root = File(LocationHelper.rootLocation).apply { mkdirs() }
		ShadowEnvironment.setExternalStorageDirectory(root.toPath())
		return root
	}

	/** Creates destination folders on "external storage" and stores them like the app does. */
	fun setDestinations(vararg folders: String) {
		folders.forEach { File(root(), it).mkdirs() }
		LocationHelper.saveFolderList(context, folders.toMutableList())
	}

	/** Runs main looper tasks until [condition] is true or the timeout expires. */
	fun waitFor(timeoutMs: Long = 10_000, condition: () -> Boolean): Boolean {
		val end = System.currentTimeMillis() + timeoutMs
		while (System.currentTimeMillis() < end) {
			shadowOf(Looper.getMainLooper()).idle()
			if (condition()) return true
			Thread.sleep(20)
		}
		shadowOf(Looper.getMainLooper()).idle()
		return condition()
	}

	fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
