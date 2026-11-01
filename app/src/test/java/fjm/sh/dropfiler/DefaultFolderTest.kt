package fjm.sh.dropfiler

import android.content.Intent
import android.view.View
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hchen.himiuix.preference.MiuixPreferenceCategory
import com.hchen.himiuix.preference.MiuixSwitchPreference
import fjm.sh.dropfiler.TestSupport.context
import fjm.sh.dropfiler.TestSupport.prefs
import fjm.sh.dropfiler.TestSupport.root
import fjm.sh.dropfiler.TestSupport.setDestinations
import fjm.sh.dropfiler.TestSupport.waitFor
import fjm.sh.dropfiler.activity.FolderListActivity
import fjm.sh.dropfiler.activity.FolderSelectActivity
import fjm.sh.dropfiler.activity.SaverActivity
import fjm.sh.dropfiler.activity.SettingsActivity
import fjm.sh.dropfiler.ui.CardItem
import fjm.sh.dropfiler.ui.CardListAdapter
import fjm.sh.dropfiler.util.LocationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.File

/**
 * The built-in, non-removable "DropFiler" (/DropFiler) destination and the setting that
 * hides it while other destinations exist.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [23, 28, 29, 34, 36])
class DefaultFolderTest {

	@Before
	fun setUp() {
		root().listFiles()?.forEach { it.deleteRecursively() }
		prefs.edit().clear().commit()
		de.cketti.library.changelog.ChangeLog(context).skipLogDialog()
		// Fresh install: no FOLDER_STORE file at all
		File(context.filesDir, "FOLDER_STORE").delete()
	}

	private fun hide(hidden: Boolean) =
		prefs.edit().putBoolean(LocationHelper.PREF_HIDE_DEFAULT_FOLDER, hidden).commit()

	private fun share(text: String) = Robolectric.buildActivity(SaverActivity::class.java,
		Intent(context, SaverActivity::class.java).setAction(Intent.ACTION_SEND)
			.setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)).setup()

	private fun sheetRows(activity: SaverActivity) =
		(activity.findViewById<RecyclerView>(R.id.list).adapter as CardListAdapter).items.filterIsInstance<CardItem.Row>()

	private fun mainRows(activity: FolderListActivity) =
		(activity.findViewById<RecyclerView>(R.id.list).adapter as CardListAdapter).items.filterIsInstance<CardItem.Row>()

	private fun launchMain(): FolderListActivity =
		Robolectric.buildActivity(FolderListActivity::class.java).setup().get().also { TestSupport.idle() }

	// Destination list ---------------------------------------------------------------------

	@Test
	fun destinations_defaultFolderFirst_hiddenOnlyWhenOthersExist() {
		assertEquals(listOf("/DropFiler"), LocationHelper.destinations(context, emptyList()))
		assertEquals(listOf("/DropFiler", "/A"), LocationHelper.destinations(context, listOf("/A")))
		hide(true)
		assertEquals(listOf("/A", "/B"), LocationHelper.destinations(context, listOf("/A", "/B")))
		// Hidden, but there is nothing else: still offered
		assertEquals(listOf("/DropFiler"), LocationHelper.destinations(context, emptyList()))
		// Never listed twice, even if it was stored by an older build
		hide(false)
		assertEquals(listOf("/DropFiler", "/A"), LocationHelper.destinations(context, listOf("/DropFiler", "/A")))
	}

	// Sharing ------------------------------------------------------------------------------

	@Test
	fun freshInstall_sharingSavesToDropFilerFolder_insteadOfAnError() {
		val controller = share("first share")
		val saved = File(root(), "DropFiler/firstshare.txt")
		assertTrue("saved without asking", waitFor { saved.length() > 0 })
		assertEquals("first share", saved.readText())
		assertTrue(waitFor { controller.get().isFinishing })
		assertEquals(context.getString(R.string.toast_save_successful), ShadowToast.getTextOfLatestToast())
	}

	@Test
	fun withOtherDestinations_sheetOffersDropFilerFirst() {
		setDestinations("/A")
		val activity = share("pick").get()
		assertTrue(waitFor { activity.findViewById<View>(R.id.list).visibility == View.VISIBLE })
		val rows = sheetRows(activity)
		assertEquals(listOf("DropFiler", "A"), rows.map { it.title.toString() })
		// Not created yet, but it is created when needed, so it can be chosen
		assertTrue(rows[0].enabled)
	}

	@Test
	fun hidden_withOtherDestinations_sheetLeavesItOut() {
		hide(true)
		setDestinations("/A", "/B")
		val activity = share("pick").get()
		assertTrue(waitFor { activity.findViewById<View>(R.id.list).visibility == View.VISIBLE })
		assertEquals(listOf("A", "B"), sheetRows(activity).map { it.title.toString() })
	}

	@Test
	fun hidden_withoutOtherDestinations_stillSavesToDropFiler() {
		hide(true)
		share("still here")
		assertTrue(waitFor { File(root(), "DropFiler/stillhere.txt").length() > 0 })
	}

	@Test
	fun deletedDropFilerFolder_isCreatedAgain() {
		File(root(), "DropFiler").apply { mkdirs() }.deleteRecursively()
		share("again")
		assertTrue(waitFor { File(root(), "DropFiler/again.txt").length() > 0 })
	}

	@Test
	fun openWith_withoutDestinations_savesToDropFiler() {
		val file = File(context.cacheDir, "doc.txt").apply { writeText("viewed") }
		Robolectric.buildActivity(SaverActivity::class.java, Intent(context, SaverActivity::class.java)
			.setAction(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.fromFile(file), "text/plain")).setup()
		assertTrue(waitFor { File(root(), "DropFiler").listFiles()?.any { it.name.endsWith("doc.txt") } == true })
	}

	// Main screen --------------------------------------------------------------------------

	@Test
	fun mainScreen_showsDropFilerFirst_notRemovableOrMovable() {
		setDestinations("/A", "/B")
		val rows = mainRows(launchMain())
		assertEquals(listOf("/DropFiler", "/A", "/B"), rows.map { it.key })
		val default = rows[0]
		assertEquals("DropFiler", default.title)
		assertEquals(context.getString(R.string.folder_list_default_summary, "/DropFiler"), default.summary)
		assertEquals("no delete button", 0, default.actionIcon)
		assertFalse("no drag handle", default.draggable)
		assertTrue(rows.drop(1).all { it.draggable && it.actionIcon != 0 })
	}

	@Test
	fun mainScreen_hidden_onlyWhenOthersExist() {
		hide(true)
		setDestinations("/A")
		assertEquals(listOf("/A"), mainRows(launchMain()).map { it.key })
		LocationHelper.saveFolderList(context, mutableListOf())
		assertEquals(listOf("/DropFiler"), mainRows(launchMain()).map { it.key })
	}

	@Test
	fun mainScreen_followsTheSettingOnResume() {
		setDestinations("/A")
		val controller = Robolectric.buildActivity(FolderListActivity::class.java).setup()
		assertEquals(listOf("/DropFiler", "/A"), mainRows(controller.get()).map { it.key })
		hide(true)
		controller.pause().resume()
		TestSupport.idle()
		assertEquals(listOf("/A"), mainRows(controller.get()).map { it.key })
	}

	@Test
	fun removingTheLastDestination_leavesDropFiler_andIsNeverStored() {
		hide(true)
		setDestinations("/A")
		val activity = launchMain()
		val list = activity.findViewById<RecyclerView>(R.id.list)
		list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY))
		list.layout(0, 0, 1080, 4000)
		list.findViewHolderForAdapterPosition(1)!!.itemView.findViewById<View>(R.id.action).performClick()
		TestSupport.idle()
		val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
		fun find(view: View): View? = when {
			view is android.widget.TextView && view.text == context.getString(R.string.folder_list_delete_confirm) -> view
			view is android.view.ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) }
			else -> null
		}
		find(dialog.window!!.decorView)!!.performClick()
		TestSupport.idle()

		assertEquals(emptyList<String>(), LocationHelper.loadFolderList(context))
		assertEquals(listOf("/DropFiler"), mainRows(activity).map { it.key })
	}

	@Test
	@Suppress("DEPRECATION")
	fun choosingDropFilerInTheFolderPicker_doesNotAddADuplicate() {
		setDestinations("/A")
		val activity = launchMain()
		val result = Intent().putExtra(FolderSelectActivity.FOLDER_SELECTED, LocationHelper.addRoot("/DropFiler"))
		val code = shadowOf(activity).run {
			activity.findViewById<View>(R.id.fab).performClick()
			nextStartedActivityForResult.requestCode
		}
		activity.onActivityResult(code, android.app.Activity.RESULT_OK, result)
		assertEquals(listOf("/A"), LocationHelper.loadFolderList(context))
		assertEquals(listOf("/DropFiler", "/A"), mainRows(activity).map { it.key })

		// When hidden, explain how to show it instead of silently doing nothing
		hide(true)
		activity.onActivityResult(code, android.app.Activity.RESULT_OK, result)
		assertEquals(listOf("/A"), LocationHelper.loadFolderList(context))
		assertEquals(context.getString(R.string.toast_default_folder_hidden), ShadowToast.getTextOfLatestToast())
	}

	// Direct Share -------------------------------------------------------------------------

	@Test
	@Config(sdk = [29, 34, 36])
	fun directShare_includesDropFiler_unlessHidden() {
		setDestinations("/A")
		fun published() = ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }
		assertEquals(listOf("folder:/DropFiler", "folder:/A"), published())
		hide(true)
		LocationHelper.saveFolderList(context, mutableListOf("/A"))
		assertEquals(listOf("folder:/A"), published())
	}

	@Test
	@Config(sdk = [29, 34, 36])
	fun directShare_toDropFiler_savesThere() {
		setDestinations("/A")
		Robolectric.buildActivity(SaverActivity::class.java,
			Intent(context, SaverActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
				.putExtra(Intent.EXTRA_TEXT, "direct").putExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID, "folder:/DropFiler")).setup()
		assertTrue(waitFor { File(root(), "DropFiler/direct.txt").length() > 0 })
		assertFalse(File(root(), "A/direct.txt").exists())
	}

	// Settings -----------------------------------------------------------------------------

	@Test
	fun settings_hasHideSwitchInSavingSection_offByDefault() {
		val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
		TestSupport.idle()
		val fragment = activity.supportFragmentManager.fragments.filterIsInstance<PreferenceFragmentCompat>().single()
		val switch = fragment.findPreference<MiuixSwitchPreference>(LocationHelper.PREF_HIDE_DEFAULT_FOLDER)!!
		assertEquals("Hide \"DropFiler\" save location", switch.title.toString())
		assertFalse(switch.isChecked)
		val saving = fragment.findPreference<MiuixPreferenceCategory>("category_saving")!!
		assertTrue((0 until saving.preferenceCount).any { saving.getPreference(it) === switch })
		// Opening Settings writes nothing
		assertFalse(prefs.contains(LocationHelper.PREF_HIDE_DEFAULT_FOLDER))
	}

	@Test
	@Config(sdk = [29, 34, 36])
	fun settings_toggle_updatesDirectShare() {
		setDestinations("/A")
		val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
		TestSupport.idle()
		val fragment = activity.supportFragmentManager.fragments.filterIsInstance<PreferenceFragmentCompat>().single()
		val switch = fragment.findPreference<MiuixSwitchPreference>(LocationHelper.PREF_HIDE_DEFAULT_FOLDER)!!
		(switch as com.hchen.himiuix.callback.OnStateChangeListener).onStateChange(true)
		TestSupport.idle()
		assertTrue(prefs.getBoolean(LocationHelper.PREF_HIDE_DEFAULT_FOLDER, false))
		assertEquals(listOf("folder:/A"), ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id })
	}
}
