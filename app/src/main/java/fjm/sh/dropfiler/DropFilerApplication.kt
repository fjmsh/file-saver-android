package fjm.sh.dropfiler

import android.app.Application
import fjm.sh.dropfiler.util.BlurSupport
import fjm.sh.dropfiler.util.ThemeHelper

/**
 * Application entry point. Applies the appearance settings before any activity is created.
 */
class DropFilerApplication : Application() {

	override fun onCreate() {
		super.onCreate()
		ThemeHelper.applyNightMode(this)
		BlurSupport.configure(this)
	}
}
