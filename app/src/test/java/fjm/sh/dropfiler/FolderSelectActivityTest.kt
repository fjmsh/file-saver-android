package fjm.sh.dropfiler

import android.app.Activity
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hchen.himiuix.MiuixAppBar
import fjm.sh.dropfiler.TestSupport.root
import fjm.sh.dropfiler.activity.FolderSelectActivity
import fjm.sh.dropfiler.ui.CardItem
import fjm.sh.dropfiler.ui.CardListAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [23, 28, 29, 31, 34, 36])
class FolderSelectActivityTest {

	@Before
	fun setUp() {
		root().listFiles()?.forEach { it.deleteRecursively() }
		File(root(), "beta").mkdirs()
		File(root(), "Alpha/Inner").mkdirs()
		File(root(), "file.txt").writeText("not a folder")
	}

	private fun rows(activity: FolderSelectActivity): List<CardItem.Row> =
		(activity.findViewById<RecyclerView>(R.id.list).adapter as CardListAdapter).items.filterIsInstance<CardItem.Row>()

	private fun click(activity: FolderSelectActivity, key: String) {
		val list = activity.findViewById<RecyclerView>(R.id.list)
		val adapter = list.adapter as CardListAdapter
		val position = adapter.items.indexOfFirst { it is CardItem.Row && it.key == key }
		list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.AT_MOST))
		list.layout(0, 0, 1080, list.measuredHeight)
		list.findViewHolderForAdapterPosition(position)!!.itemView.findViewById<View>(R.id.row).performClick()
		TestSupport.idle()
	}

	@Test
	fun folderPicker_hasNoRowDividers() {
		val activity = Robolectric.buildActivity(FolderSelectActivity::class.java).setup().get()
		TestSupport.idle()
		val list = activity.findViewById<RecyclerView>(R.id.list)
		list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.AT_MOST))
		list.layout(0, 0, 1080, list.measuredHeight)
		(0 until list.adapter!!.itemCount).forEach {
			list.findViewHolderForAdapterPosition(it)?.itemView?.findViewById<View>(R.id.row_divider)?.let { divider ->
				assertEquals(View.GONE, divider.visibility)
			}
		}
	}

	@Test
	fun listsOnlyFolders_sortedCaseInsensitively_thenSelects() {
		val activity = Robolectric.buildActivity(FolderSelectActivity::class.java).setup().get()
		TestSupport.idle()

		// current folder, parent, then sub folders
		assertEquals(listOf("current", "back", "/Alpha", "/beta"), rows(activity).map { it.key })

		click(activity, "/Alpha")
		assertEquals("Alpha", activity.findViewById<MiuixAppBar>(R.id.appbar).title.toString())
		assertEquals(listOf("current", "back", "/Inner"), rows(activity).map { it.key })

		activity.findViewById<View>(R.id.navigation_here).performClick()
		val result = shadowOf(activity)
		assertEquals(Activity.RESULT_OK, result.resultCode)
		assertEquals(File(root(), "Alpha").absolutePath,
			result.resultIntent.getStringExtra(FolderSelectActivity.FOLDER_SELECTED))
		assertTrue(activity.isFinishing)
	}

	private fun findWithText(view: View, text: String): View? {
		if (view is android.widget.TextView && view.text.toString() == text) return view
		if (view is android.view.ViewGroup) for (i in 0 until view.childCount) findWithText(view.getChildAt(i), text)?.let { return it }
		return null
	}

	/** Opens "New folder", enters [name] and presses Create. Returns the dialog. */
	private fun createFolder(activity: FolderSelectActivity, name: String): android.app.Dialog {
		activity.findViewById<View>(R.id.new_folder).performClick()
		TestSupport.idle()
		val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
		val decor = dialog.window!!.decorView
		decor.findViewById<com.hchen.himiuix.widget.MiuixEditText>(R.id.new_folder_name).setText(name)
		findWithText(decor, activity.getString(R.string.folder_select_new_folder_create))!!.performClick()
		TestSupport.idle()
		return dialog
	}

	@Test
	fun newFolder_isCreatedAndOpened() {
		val activity = Robolectric.buildActivity(FolderSelectActivity::class.java).setup().get()
		TestSupport.idle()
		assertTrue(activity.findViewById<View>(R.id.new_folder).isEnabled)

		val dialog = createFolder(activity, "  Memes ")
		assertFalse(dialog.isShowing)
		assertTrue(File(root(), "Memes").isDirectory)
		assertEquals("Memes", activity.findViewById<MiuixAppBar>(R.id.appbar).title.toString())

		activity.findViewById<View>(R.id.navigation_here).performClick()
		assertEquals(File(root(), "Memes").absolutePath,
			shadowOf(activity).resultIntent.getStringExtra(FolderSelectActivity.FOLDER_SELECTED))
	}

	@Test
	fun newFolder_invalidNamesAreRejected() {
		val activity = Robolectric.buildActivity(FolderSelectActivity::class.java).setup().get()
		TestSupport.idle()
		for (name in listOf("", "   ", "a/b", "..", "Alpha")) {
			val dialog = createFolder(activity, name)
			assertTrue("dialog stays open for '$name'", dialog.isShowing)
			val error = dialog.window!!.decorView.findViewById<android.widget.TextView>(R.id.new_folder_error)
			assertEquals(View.VISIBLE, error.visibility)
			dialog.dismiss()
		}
		assertFalse(File(root(), "a").exists())
	}

	@Test
	fun back_goesUpOneLevel_thenCancelsAtRoot() {
		val activity = Robolectric.buildActivity(FolderSelectActivity::class.java).setup().get()
		TestSupport.idle()
		click(activity, "/Alpha")

		activity.onBackPressedDispatcher.onBackPressed()
		TestSupport.idle()
		assertEquals(listOf("current", "back", "/Alpha", "/beta"), rows(activity).map { it.key })

		activity.onBackPressedDispatcher.onBackPressed()
		assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
		assertTrue(activity.isFinishing)
	}

	@Test
	fun readOnlyFolder_disablesSelection() {
		val readOnly = File(root(), "Alpha")
		val activity = Robolectric.buildActivity(FolderSelectActivity::class.java).setup().get()
		TestSupport.idle()
		readOnly.setWritable(false)
		try {
			click(activity, "/Alpha")
			// Robolectric runs as the file owner, so only assert when the flag took effect
			if (!readOnly.canWrite()) {
				assertEquals(View.GONE, activity.findViewById<View>(R.id.navigation_here).visibility)
				assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.navigation_read_only).visibility)
				assertFalse(activity.findViewById<View>(R.id.new_folder).isEnabled)
			}
		} finally {
			readOnly.setWritable(true)
		}
	}
}
