package fjm.sh.dropfiler.service

import android.content.ComponentName
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.chooser.ChooserTarget
import android.service.chooser.ChooserTargetService
import androidx.annotation.RequiresApi
import fjm.sh.dropfiler.activity.SaverActivity
import fjm.sh.dropfiler.util.DebugLogger
import fjm.sh.dropfiler.util.DirectShareIcon
import fjm.sh.dropfiler.util.LocationHelper

/**
 * Service for creating direct share options (Android 6.0 - 9).
 * Android 10+ uses sharing shortcuts instead, see DirectShareHelper.
 */
@RequiresApi(Build.VERSION_CODES.M)
@Suppress("DEPRECATION")
class LocationChooserTargetService : ChooserTargetService() {

	companion object {
		private const val targetRanking = 1.0f
		private const val targetNameMaxLength = 15
	}

	private val componentName = ComponentName(SaverActivity::class.java.`package`?.name ?: "LocationChooserTargetService", SaverActivity::class.java.name)
	private lateinit var log: DebugLogger

	override fun onGetChooserTargets(targetActivityName: ComponentName?, matchedFilter: IntentFilter?): MutableList<ChooserTarget> {
		log = DebugLogger(this)
		log.d("Creating direct share targets")

		return LocationHelper.destinations(this).map {
				val name = if (it.length > targetNameMaxLength) {
					it.substring(it.length - targetNameMaxLength)
				} else it
				log.d("Adding $it to targets")
				val extras = Bundle()
				extras.putString(SaverActivity.DIRECT_SHARE_FOLDER, it)
				ChooserTarget(name, Icon.createWithBitmap(DirectShareIcon.generate(this, it)), targetRanking, componentName, extras)
			}.toMutableList()
	}

}
