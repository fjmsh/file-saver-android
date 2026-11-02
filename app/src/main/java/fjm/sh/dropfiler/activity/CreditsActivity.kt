package fjm.sh.dropfiler.activity

import android.content.pm.PackageManager
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.ConfigurationCompat
import androidx.core.view.ViewCompat
import fjm.sh.dropfiler.R
import java.util.Locale
import fjm.sh.dropfiler.util.DebugLogger

/**
 * Credits activity.
 */
class CreditsActivity : BaseActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.credits_activity)
		setUpAppBar(getString(R.string.title_activity_credits), showUp = true)
	}

	/**
	 * Credits content. A Miuix XML Fragment so that the large title collapses on scroll.
	 */
	class CreditsFragment : com.hchen.himiuix.fragment.Fragment() {

		private val defaultLocale = Locale("en").language

		private lateinit var log: DebugLogger

		override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
			inflater.inflate(R.layout.credits_fragment, container, false)

		override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
			super.onViewCreated(view, savedInstanceState)
			val context = requireContext()
			log = DebugLogger(context)

			// Version
			try {
				val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
				view.findViewById<TextView>(R.id.credits_version).text = resources.getString(R.string.credits_version, versionName)
			} catch (e: PackageManager.NameNotFoundException) {
				log.e("Unable to get package version", e)
			}

			ViewCompat.setAccessibilityHeading(view.findViewById(R.id.credits_section_about), true)
			ViewCompat.setAccessibilityHeading(view.findViewById(R.id.credits_section_licenses), true)

			// Linkify
			listOf(R.id.credits_creator, R.id.credits_content1, R.id.credits_content2,
					R.id.credits_content3, R.id.credits_miuix_body).forEach {
				view.findViewById<TextView>(it).movementMethod = LinkMovementMethod.getInstance()
			}
			if (getCurrentLocale().language == defaultLocale){
				// English, hide the translator info
				view.findViewById<TextView>(R.id.credits_content_translator).visibility = View.GONE
			} else {
				view.findViewById<TextView>(R.id.credits_content_translator).movementMethod = LinkMovementMethod.getInstance()
			}
		}

		/**
		 * The currently applied locale.
		 */
		private fun getCurrentLocale(): Locale =
			ConfigurationCompat.getLocales(resources.configuration).get(0) ?: Locale.getDefault()
	}
}
