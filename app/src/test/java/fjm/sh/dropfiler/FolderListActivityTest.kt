package fjm.sh.dropfiler

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import fjm.sh.dropfiler.TestSupport.context
import fjm.sh.dropfiler.TestSupport.prefs
import fjm.sh.dropfiler.TestSupport.root
import fjm.sh.dropfiler.TestSupport.setDestinations
import fjm.sh.dropfiler.activity.FolderListActivity
import fjm.sh.dropfiler.activity.FolderSelectActivity
import fjm.sh.dropfiler.ui.CardItem
import fjm.sh.dropfiler.ui.CardListAdapter
import fjm.sh.dropfiler.util.LocationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [23, 26, 28, 29, 30, 31, 33, 34, 35, 36])
class FolderListActivityTest {

	@Before
	fun setUp() {
		root().listFiles()?.forEach { it.deleteRecursively() }
		prefs.edit().clear().commit()
		// Mark the change log as seen
		de.cketti.library.changelog.ChangeLog(context).skipLogDialog()
	}

	private fun launch(): FolderListActivity {
		val activity = Robolectric.buildActivity(FolderListActivity::class.java).setup().get()
		TestSupport.idle()
		return activity
	}

	private fun items(activity: FolderListActivity): List<CardItem> =
		(activity.findViewById<RecyclerView>(R.id.list).adapter as CardListAdapter).items

	@Test
	fun noDestinations_showsEmptyState() {
		val activity = launch()
		val items = items(activity)
		assertEquals(1, items.size)
		assertTrue(items[0] is CardItem.Message)
		assertEquals(View.GONE, activity.findViewById<View>(R.id.fab).visibility)
	}

	@Test
	fun existingDestinations_areShownInStoredOrder() {
		setDestinations("/DCIM/Memes", "/Download", "")
		val activity = launch()

		val rows = items(activity).filterIsInstance<CardItem.Row>()
		assertEquals(listOf("/DCIM/Memes", "/Download", ""), rows.map { it.key })
		assertEquals("Memes", rows[0].title)
		assertEquals("/DCIM/Memes", rows[0].summary)
		assertEquals(File.separator, rows[2].summary)
		assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.fab).visibility)
	}

	@Test
	fun locationSelect_addsNonDeletableFirstRow() {
		setDestinations("/A")
		prefs.edit().putBoolean("location_select", true).commit()
		val rows = items(launch()).filterIsInstance<CardItem.Row>()
		assertEquals(2, rows.size)
		assertEquals(0, rows[0].actionIcon)
		assertTrue(rows[1].actionIcon != 0)
	}

	@Test
	@Suppress("DEPRECATION")
	fun folderSelectResult_appendsWithoutDuplicates_keepingCustomOrder() {
		setDestinations("/d", "/b")
		val activity = launch()

		val data = Intent().putExtra(FolderSelectActivity.FOLDER_SELECTED, LocationHelper.rootLocation + "/C")
		activity.onActivityResult(FolderListActivity.FOLDER_SELECT_REQUEST_CODE, Activity.RESULT_OK, data)
		activity.onActivityResult(FolderListActivity.FOLDER_SELECT_REQUEST_CODE, Activity.RESULT_OK, data)

		assertEquals(listOf("/d", "/b", "/C"), LocationHelper.loadFolderList(context))
		assertEquals(listOf("/d", "/b", "/C"), items(activity).filterIsInstance<CardItem.Row>().map { it.key })
	}

	private fun laidOutList(activity: FolderListActivity): RecyclerView {
		val list = activity.findViewById<RecyclerView>(R.id.list)
		list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY))
		list.layout(0, 0, 1080, 4000)
		return list
	}

	private fun touch(view: View, action: Int, x: Float, y: Float, downTime: Long) {
		val event = android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(), action, x, y, 0)
		view.dispatchTouchEvent(event)
		event.recycle()
	}

	private fun itemTouchHelper(activity: FolderListActivity): androidx.recyclerview.widget.ItemTouchHelper =
		FolderListActivity::class.java.getDeclaredField("itemTouchHelper").apply { isAccessible = true }
			.get(activity) as androidx.recyclerview.widget.ItemTouchHelper

	private fun helperField(helper: androidx.recyclerview.widget.ItemTouchHelper, name: String): Any? =
		androidx.recyclerview.widget.ItemTouchHelper::class.java.getDeclaredField(name)
			.apply { isAccessible = true }.get(helper)

	@Test
	@Config(qualifiers = "w411dp-h1600dp")
	fun dragHandle_startsDrag() {
		setDestinations("/A", "/B")
		val activity = launch()
		val list = activity.findViewById<RecyclerView>(R.id.list)
		val holder = list.findViewHolderForAdapterPosition(1)!!
		val handle = holder.itemView.findViewById<View>(R.id.drag_handle)
		assertEquals(View.VISIBLE, handle.visibility)

		val handleLocation = IntArray(2).also { handle.getLocationInWindow(it) }
		val listLocation = IntArray(2).also { list.getLocationInWindow(it) }
		val x = (handleLocation[0] - listLocation[0] + handle.width / 2).toFloat()
		val y = (handleLocation[1] - listLocation[1] + handle.height / 2).toFloat()
		val down = android.os.SystemClock.uptimeMillis()
		touch(list, android.view.MotionEvent.ACTION_DOWN, x, y, down)
		val helper = itemTouchHelper(activity)
		assertEquals("touching the handle starts dragging that row", holder, helperField(helper, "mSelected"))

		// Cancel before running the looper: Robolectric does not pace ItemTouchHelper's frames
		touch(list, android.view.MotionEvent.ACTION_CANCEL, x, y, down)
		assertEquals(null, helperField(helper, "mSelected"))
	}

	@Test
	@Config(qualifiers = "w411dp-h1600dp")
	fun drag_reordersDestinations_andSavesOrderOnDrop() {
		setDestinations("/A", "/B", "/C")
		prefs.edit().putBoolean("location_select", true).commit()
		val activity = launch()
		val list = activity.findViewById<RecyclerView>(R.id.list)
		val callback = helperField(itemTouchHelper(activity), "mCallback")
			as androidx.recyclerview.widget.ItemTouchHelper.Callback
		fun holder(position: Int) = list.findViewHolderForAdapterPosition(position)!!

		// 0 header, 1 location select, 2..4 destinations
		assertEquals(0, callback.getMovementFlags(list, holder(0)))
		assertEquals("location select row stays first", 0, callback.getMovementFlags(list, holder(1)))
		assertTrue(callback.getMovementFlags(list, holder(2)) != 0)
		assertFalse("cannot drop above location select", callback.canDropOver(list, holder(2), holder(1)))

		// Drag "/A" down two places, then drop
		assertTrue(callback.onMove(list, holder(2), holder(3)))
		TestSupport.idle()
		assertTrue(callback.onMove(list, holder(3), holder(4)))
		TestSupport.idle()
		assertEquals("not saved before the drop", listOf("/A", "/B", "/C"), LocationHelper.loadFolderList(context))
		callback.clearView(list, holder(4))
		TestSupport.idle()

		assertEquals(listOf("/B", "/C", "/A"), LocationHelper.loadFolderList(context))
		assertEquals(listOf("location_select", "/B", "/C", "/A"),
			items(activity).filterIsInstance<CardItem.Row>().map { it.key })
	}

	@Test
	fun accessibilityActions_moveDestinationsUpAndDown() {
		setDestinations("/A", "/B", "/C")
		val activity = launch()
		val list = laidOutList(activity)

		fun actionsOf(position: Int): List<android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction> {
			val row = list.findViewHolderForAdapterPosition(position)!!.itemView.findViewById<View>(R.id.row)
			val info = android.view.accessibility.AccessibilityNodeInfo.obtain()
			row.onInitializeAccessibilityNodeInfo(info)
			return info.actionList
		}
		fun perform(position: Int, label: Int) {
			val row = list.findViewHolderForAdapterPosition(position)!!.itemView.findViewById<View>(R.id.row)
			val action = actionsOf(position).first { it.label == context.getString(label) }
			assertTrue(row.performAccessibilityAction(action.id, null))
			TestSupport.idle()
			laidOutList(activity)
		}

		val labels = { position: Int -> actionsOf(position).mapNotNull { it.label?.toString() } }
		// First destination can only move down, last only up
		assertFalse(labels(1).contains(context.getString(R.string.folder_list_move_up)))
		assertTrue(labels(1).contains(context.getString(R.string.folder_list_move_down)))
		assertFalse(labels(3).contains(context.getString(R.string.folder_list_move_down)))

		perform(1, R.string.folder_list_move_down)
		assertEquals(listOf("/B", "/A", "/C"), LocationHelper.loadFolderList(context))
		perform(3, R.string.folder_list_move_up)
		assertEquals(listOf("/B", "/C", "/A"), LocationHelper.loadFolderList(context))
	}

	@Test
	fun locationSelectRow_isNotReorderable() {
		setDestinations("/A", "/B")
		prefs.edit().putBoolean("location_select", true).commit()
		val rows = items(launch()).filterIsInstance<CardItem.Row>()
		assertFalse(rows[0].draggable)
		assertTrue(rows.drop(1).all { it.draggable })
	}

	@Test
	fun delete_asksForConfirmation_thenRemovesDestination() {
		setDestinations("/A", "/B")
		val activity = launch()
		val list = activity.findViewById<RecyclerView>(R.id.list)
		list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST))
		list.layout(0, 0, 1080, list.measuredHeight)

		// Position 0 is the section header
		list.findViewHolderForAdapterPosition(1)!!.itemView.findViewById<View>(R.id.action).performClick()
		TestSupport.idle()
		assertEquals("nothing removed before confirming", listOf("/A", "/B"), LocationHelper.loadFolderList(context))

		val dialog = ShadowDialog.getLatestDialog()
		assertNotNull(dialog)
		val confirm = findViewWithText(dialog.window!!.decorView, context.getString(R.string.folder_list_delete_confirm))
		assertNotNull("confirm button", confirm)
		confirm!!.performClick()
		TestSupport.idle()

		assertEquals(listOf("/B"), LocationHelper.loadFolderList(context))
		assertEquals(listOf("/B"), items(activity).filterIsInstance<CardItem.Row>().map { it.key })
	}

	@Test
	fun launch_showsNoSupportPopup() {
		launch()
		assertEquals(null, ShadowDialog.getLatestDialog())
	}

	@Test
	fun githubMenuItem_opensProfile() {
		val activity = launch()
		val toolbar = activity.findViewById<com.hchen.himiuix.MiuixAppBar>(R.id.appbar).toolbar
		assertEquals(null, toolbar.menu.findItem(context.resources.getIdentifier("action_support", "id", context.packageName)))
		val item = toolbar.menu.findItem(R.id.action_github)
		assertEquals("fjmsh", item.title.toString())
		assertNotNull(item.icon)

		activity.onOptionsItemSelected(item)
		val started = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
		assertEquals(Intent.ACTION_VIEW, started.action)
		assertEquals("https://github.com/fjmsh", started.dataString)
	}

	@Test
	fun destinations_haveDividersBetweenRows() {
		setDestinations("/A", "/B", "/C")
		val list = laidOutList(launch())
		val dividers = (0 until list.adapter!!.itemCount).mapNotNull { position ->
			list.findViewHolderForAdapterPosition(position)?.itemView?.findViewById<View>(R.id.row_divider)?.visibility
		}
		// The section header has no divider view; no line above the first row of the card
		assertEquals(listOf(View.GONE, View.VISIBLE, View.VISIBLE), dividers)
	}

	@Test
	fun overflowMenu_titlesAndRoundedSquareIcons() {
		val activity = launch()
		val menu = activity.findViewById<com.hchen.himiuix.MiuixAppBar>(R.id.appbar).toolbar.menu
		assertEquals(listOf("Settings", "fjmsh", "Changelog", "About"),
			(0 until menu.size()).map { menu.getItem(it).title.toString() })
		val tile = activity.resources.getDimensionPixelSize(R.dimen.fs_menu_icon_tile)
		(0 until menu.size()).map { menu.getItem(it) }.forEach {
			val icon = it.icon
			assertNotNull("${it.title} icon", icon)
			assertTrue("${it.title} uses an icon tile", icon is android.graphics.drawable.LayerDrawable)
			assertEquals(tile, icon!!.intrinsicWidth)
			assertEquals(tile, icon.intrinsicHeight)
		}
		// The popup minimum width is 20% below AppCompat's 196dp
		assertEquals((157 * activity.resources.displayMetrics.density).toInt(),
			activity.resources.getDimensionPixelSize(R.dimen.fs_popup_min_width))
	}

	@Test
	fun addButton_opensFolderSelect() {
		setDestinations("/A")
		val activity = launch()
		activity.findViewById<View>(R.id.fab).performClick()
		val next = org.robolectric.Shadows.shadowOf(activity).nextStartedActivityForResult
		assertEquals(FolderSelectActivity::class.java.name, next.intent.component!!.className)
		assertEquals(FolderListActivity.FOLDER_SELECT_REQUEST_CODE, next.requestCode)
	}

	@Test
	@Config(qualifiers = "night")
	fun darkTheme_inflates() {
		setDestinations("/A")
		val activity = launch()
		assertTrue(activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
				android.content.res.Configuration.UI_MODE_NIGHT_YES)
		assertEquals(2, items(activity).size)
		// Dark backgrounds are #131313, not pure black
		assertEquals(0xFF131313.toInt(), androidx.core.content.ContextCompat.getColor(activity, R.color.fs_window_background))
	}

	private fun findViewWithText(view: View, text: String): View? {
		if (view is TextView && view.text.toString() == text) return view
		if (view is ViewGroup) {
			for (i in 0 until view.childCount) {
				findViewWithText(view.getChildAt(i), text)?.let { return it }
			}
		}
		return null
	}
}
