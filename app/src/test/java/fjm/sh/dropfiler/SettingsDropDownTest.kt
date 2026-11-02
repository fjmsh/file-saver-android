package fjm.sh.dropfiler

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hchen.himiuix.MiuixDropDownView
import fjm.sh.dropfiler.TestSupport.prefs
import fjm.sh.dropfiler.activity.SettingsActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Drop-down preferences must react to real taps (a real finger tap includes MOVE events,
 * which previously cancelled the click in Miuix drop-downs).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [23, 28, 31, 34, 36])
class SettingsDropDownTest {

	@Before
	fun setUp() {
		prefs.edit().clear().commit()
	}

	@After
	fun tearDown() {
		AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
	}

	/** DOWN, a small MOVE and UP in the middle of the view, like a finger tap. */
	private fun fingerTap(view: View) {
		val x = view.width / 2f
		val y = view.height / 2f
		val t = SystemClock.uptimeMillis()
		view.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
		view.dispatchTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_MOVE, x + 2, y + 1, 0))
		view.dispatchTouchEvent(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x + 2, y + 1, 0))
		TestSupport.idle()
	}

	private fun layout(view: View, width: Int, maxHeight: Int) {
		view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST))
		view.layout(0, 0, width, view.measuredHeight)
	}

	private fun <T : View> findAll(view: View, type: Class<T>, out: MutableList<T> = mutableListOf()): List<T> {
		if (type.isInstance(view)) out.add(type.cast(view)!!)
		if (view is ViewGroup) for (i in 0 until view.childCount) findAll(view.getChildAt(i), type, out)
		return out
	}

	private fun choose(title: String, option: Int) {
		val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
		TestSupport.idle()
		val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as PreferenceFragmentCompat
		val list = fragment.listView
		layout(list, 1080, 20000)
		val row = findAll(list, MiuixDropDownView::class.java).first { it.title.toString() == title }

		fingerTap(row)
		val dialog = ShadowDialog.getLatestDialog()
		val options = findAll(dialog.window!!.decorView, RecyclerView::class.java).first()
		layout(options, 600, 2000)
		fingerTap(options.getChildAt(option))

		assertFalse("drop-down closes after choosing", dialog.isShowing)
	}

	@Test
	fun theme_optionCanBeChosen() {
		// "Light" keeps the test's (light) configuration, so the activity is not recreated;
		// Robolectric's simulated recreate does not cope with the manual layout used here.
		choose("Theme", 1)
		assertEquals("light", prefs.getString("theme_mode", null))
		assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())
	}

	@Test
	fun fileExists_optionCanBeChosen_andKeepsLegacyFormat() {
		choose("Behaviour if file already exists", 2)
		assertEquals("Postfix", prefs.getString("file_exists", null))
	}
}
