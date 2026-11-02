package fjm.sh.dropfiler.activity

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.hchen.himiuix.MiuixAppBar
import com.hchen.himiuix.helper.WindowInsetsHelper
import fjm.sh.dropfiler.R
import fjm.sh.dropfiler.util.ThemeHelper

/**
 * Common setup for the Miuix styled screens: dynamic colors, edge-to-edge drawing
 * (required by MiuixAppBar) and recreation when the appearance settings change.
 */
abstract class BaseActivity : AppCompatActivity() {

	private var themeSignature: String? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		ThemeHelper.applyDynamicColors(this)
		themeSignature = ThemeHelper.signature(this)
		super.onCreate(savedInstanceState)
		WindowCompat.setDecorFitsSystemWindows(window, false)
		WindowInsetsHelper.initialWindowInsetsListener(this)
	}

	override fun onResume() {
		super.onResume()
		if (themeSignature != ThemeHelper.signature(this)) {
			// Appearance changed in Settings while this activity was in the back stack
			recreate()
		}
	}

	/**
	 * Configures the Miuix app bar with a large collapsible title.
	 * @param showUp Show the navigation (back) button.
	 */
	protected fun setUpAppBar(title: CharSequence, showUp: Boolean): MiuixAppBar {
		val appBar = findViewById<MiuixAppBar>(R.id.appbar)
		appBar.title = title
		val toolbar = appBar.toolbar
		if (showUp) {
			toolbar.navigationContentDescription = getString(R.string.navigate_up)
			toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
		} else {
			toolbar.navigationIcon = null
		}
		return appBar
	}

	/**
	 * Keeps a view clear of the system bars (navigation bar / gesture area) by adding the
	 * inset to its initial bottom margin or padding.
	 */
	protected fun applyBottomInset(view: View, asMargin: Boolean) {
		val initialPadding = view.paddingBottom
		val initialMargin = (view.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
		ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
			val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
			if (asMargin) {
				(view.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
					it.bottomMargin = initialMargin + bottom
					view.layoutParams = it
				}
			} else {
				view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, initialPadding + bottom)
			}
			insets
		}
	}
}
