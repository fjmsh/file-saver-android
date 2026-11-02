package fjm.sh.dropfiler.util

import android.content.Context
import android.os.Build
import android.view.WindowManager
import com.hchen.himiuix.utils.MiuiSuperBlur

/**
 * Decides whether the Xiaomi-specific background blur used by Miuix XML may be enabled.
 *
 * Miuix XML only checks that the private HyperOS View blur methods exist. That is not
 * enough: the device may have blur turned off (battery saver, low-end hardware, "advanced
 * textures" disabled) in which case the app bar would become transparent. Blur is therefore
 * only allowed when the platform also reports cross-window blur as enabled (Android 12+).
 * Everywhere else Miuix XML falls back to a plain surface color, so navigation never
 * depends on blur.
 */
object BlurSupport {

	fun isSupported(context: Context): Boolean {
		if (!MiuiSuperBlur.isBlurAvailable()) return false
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
		val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
		return windowManager?.isCrossWindowBlurEnabled == true
	}

	fun configure(context: Context) {
		MiuiSuperBlur.setBlurAllowed(isSupported(context))
	}
}
