package fjm.sh.dropfiler

import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.MimeTypeMap
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import fjm.sh.dropfiler.TestSupport.context
import fjm.sh.dropfiler.TestSupport.prefs
import fjm.sh.dropfiler.TestSupport.root
import fjm.sh.dropfiler.TestSupport.setDestinations
import fjm.sh.dropfiler.TestSupport.waitFor
import fjm.sh.dropfiler.activity.SaverActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.io.File

/**
 * Share target behaviour: the saving logic must be unchanged by the UI modernisation.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [23, 26, 28, 29, 30, 31, 33, 34, 35, 36])
class SaverActivityTest {

	@Before
	fun setUp() {
		root().listFiles()?.forEach { it.deleteRecursively() }
		prefs.edit().clear().commit()
		// These tests are about the user's own destinations: hide the built-in DropFiler
		// folder, which is then only used when there is no other destination (DefaultFolderTest)
		prefs.edit().putBoolean(fjm.sh.dropfiler.util.LocationHelper.PREF_HIDE_DEFAULT_FOLDER, true).commit()
		// Robolectric's MimeTypeMap may be empty; register what the tests use
		shadowOf(MimeTypeMap.getSingleton()).apply {
			addExtensionMimeTypeMapping("txt", "text/plain")
			addExtensionMimeTypeMapping("jpg", "image/jpeg")
			addExtensionMimeTypeMapping("mp4", "video/mp4")
			addExtensionMimeTypeMapping("pdf", "application/pdf")
		}
	}

	private fun textIntent(text: String) = Intent(context, SaverActivity::class.java)
		.setAction(Intent.ACTION_SEND)
		.setType("text/plain")
		.putExtra(Intent.EXTRA_TEXT, text)

	private fun sourceFile(name: String, content: String): Uri {
		val file = File(context.cacheDir, name)
		file.writeText(content)
		return Uri.fromFile(file)
	}

	private fun streamIntent(type: String, uri: Uri) = Intent(context, SaverActivity::class.java)
		.setAction(Intent.ACTION_SEND)
		.setType(type)
		.putExtra(Intent.EXTRA_STREAM, uri)

	private fun launch(intent: Intent): ActivityController<SaverActivity> =
		Robolectric.buildActivity(SaverActivity::class.java, intent).setup()

	@Test
	fun singleDestination_savesTextAutomatically() {
		setDestinations("/Saved")
		val controller = launch(textIntent("hello world"))

		val saved = File(root(), "Saved/helloworld.txt")
		assertTrue("text should be saved without asking", waitFor { saved.exists() && saved.length() > 0 })
		assertEquals("hello world", saved.readText())
		assertTrue(waitFor { controller.get().isFinishing })
	}

	private fun notifications() =
		shadowOf(context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
			.allNotifications

	private fun android.app.Notification.title() = extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString()
	private fun android.app.Notification.text() = extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString()

	@Test
	fun successfulSave_postsResultNotification() {
		setDestinations("/Saved")
		launch(textIntent("hello world"))
		assertTrue(waitFor { notifications().isNotEmpty() })

		val notification = notifications().single()
		assertEquals(context.getString(R.string.notification_saved_title), notification.title())
		assertEquals("helloworld.txt saved to /Saved", notification.text())
	}

	@Test
	fun failedSave_postsResultNotificationWithReason() {
		setDestinations("/Saved")
		File(root(), "Saved/helloworld.txt").writeText("old")
		prefs.edit().putString("file_exists", "Nothing").commit()
		launch(textIntent("hello world"))
		assertTrue(waitFor { notifications().isNotEmpty() })

		val notification = notifications().single()
		assertEquals(context.getString(R.string.notification_failed_title), notification.title())
		assertEquals(context.getString(R.string.toast_save_file_exists_fail), notification.text())
	}

	@Test
	fun multipleFiles_postOneNotification() {
		setDestinations("/Batch")
		val intent = Intent(context, SaverActivity::class.java)
			.setAction(Intent.ACTION_SEND_MULTIPLE)
			.setType("image/*")
			.putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(
				sourceFile("a.jpg", "1"), sourceFile("b.jpg", "2")))
		launch(intent)
		assertTrue(waitFor { notifications().isNotEmpty() })
		assertEquals("2 items saved to /Batch", notifications().single().text())
	}

	@Test
	fun completedDownload_postsResultNotification() {
		fjm.sh.dropfiler.receiver.DownloadBroadcastReceiver()
			.onReceive(context, Intent(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE))
		assertEquals(context.getString(R.string.notification_saved_title), notifications().single().title())
	}

	private fun rows(activity: SaverActivity) =
		(activity.findViewById<RecyclerView>(R.id.list).adapter as fjm.sh.dropfiler.ui.CardListAdapter)
			.items.filterIsInstance<fjm.sh.dropfiler.ui.CardItem.Row>()

	private fun clickRow(activity: SaverActivity, title: String) {
		val list = activity.findViewById<RecyclerView>(R.id.list)
		val adapter = list.adapter as fjm.sh.dropfiler.ui.CardListAdapter
		val position = adapter.items.indexOfFirst { it is fjm.sh.dropfiler.ui.CardItem.Row && it.title == title }
		list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.AT_MOST))
		list.layout(0, 0, 1080, list.measuredHeight)
		// The sheet list is height-limited; bring the row on screen like a user would
		list.scrollToPosition(position)
		list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.AT_MOST))
		list.layout(0, 0, 1080, list.measuredHeight)
		list.findViewHolderForAdapterPosition(position)!!.itemView.findViewById<View>(R.id.row).performClick()
	}

	@Test
	fun shareSheet_followsTheOrderSetOnTheMainScreen() {
		setDestinations("/Zeta", "/Alpha", "/Mid")
		val activity = launch(textIntent("order")).get()
		assertTrue(waitFor { activity.findViewById<View>(R.id.list).visibility == View.VISIBLE })
		assertEquals(listOf("Zeta", "Alpha", "Mid"), rows(activity).map { it.title.toString() })
	}

	@Test
	fun shareSheet_hasDividersBetweenDestinations() {
		setDestinations("/A", "/B", "/C")
		val activity = launch(textIntent("dividers")).get()
		assertTrue(waitFor { activity.findViewById<View>(R.id.list).visibility == View.VISIBLE })
		val list = activity.findViewById<RecyclerView>(R.id.list)
		list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.AT_MOST))
		list.layout(0, 0, 1080, list.measuredHeight)
		val dividers = (0 until 3).map {
			list.findViewHolderForAdapterPosition(it)!!.itemView.findViewById<View>(R.id.row_divider).visibility
		}
		assertEquals(listOf(View.GONE, View.VISIBLE, View.VISIBLE), dividers)
	}

	@Test
	fun missingSingleDestination_asksForAnotherFolderInsteadOfCrashing() {
		setDestinations("/Gone")
		File(root(), "Gone").deleteRecursively()
		val activity = launch(streamIntent("image/jpeg", sourceFile("photo.jpg", "x"))).get()

		assertTrue(waitFor { activity.findViewById<View>(R.id.list).visibility == View.VISIBLE })
		assertFalse(activity.isFinishing)
		val rows = rows(activity)
		assertEquals(context.getString(R.string.saver_missing_title), rows[0].title)
		assertTrue(rows[0].danger)
		// The missing destination is shown but cannot be chosen
		assertFalse(rows.first { it.title == "Gone" }.enabled)
		// The system picker is always offered
		assertTrue(rows.any { it.title == context.getString(R.string.saver_choose_other_folder) })
		assertFalse(File(root(), "Gone").exists())
	}

	@Test
	fun missingDestination_anotherFolderCanBeChosen() {
		setDestinations("/Gone", "/Ok")
		File(root(), "Gone").deleteRecursively()
		val activity = launch(textIntent("other folder")).get()

		assertTrue(waitFor { activity.findViewById<View>(R.id.list).visibility == View.VISIBLE })
		assertFalse(rows(activity).first { it.title == "Gone" }.clickable)
		clickRow(activity, "Ok")
		assertTrue(waitFor { File(root(), "Ok/otherfolder.txt").length() > 0 })
	}

	@Test
	fun missingDirectShareFolder_asksForAnotherFolder() {
		setDestinations("/Gone", "/Ok")
		File(root(), "Gone").deleteRecursively()
		val activity = launch(textIntent("direct").putExtra(SaverActivity.DIRECT_SHARE_FOLDER, "/Gone")).get()

		assertTrue(waitFor { activity.findViewById<View>(R.id.list).visibility == View.VISIBLE })
		assertEquals(context.getString(R.string.saver_missing_title), rows(activity)[0].title)
		clickRow(activity, "Ok")
		assertTrue(waitFor { File(root(), "Ok/direct.txt").length() > 0 })
	}

	@Test
	@Suppress("DEPRECATION")
	fun missingDestination_systemPickerCanBeChosen() {
		setDestinations("/Gone")
		File(root(), "Gone").deleteRecursively()
		val activity = launch(textIntent("picked")).get()
		assertTrue(waitFor { activity.findViewById<View>(R.id.list).visibility == View.VISIBLE })

		clickRow(activity, context.getString(R.string.saver_choose_other_folder))
		assertTrue(waitFor { shadowOf(activity).peekNextStartedActivityForResult() != null })
		assertEquals(Intent.ACTION_CREATE_DOCUMENT, shadowOf(activity).nextStartedActivityForResult.intent.action)
	}

	@Test
	fun multipleDestinations_showsSelector_andSavesToChosenFolder() {
		setDestinations("/A", "/B")
		val activity = launch(textIntent("pick me")).get()

		val list = activity.findViewById<RecyclerView>(R.id.list)
		assertTrue("destination list shown", waitFor { list.visibility == View.VISIBLE && list.adapter?.itemCount == 2 })
		assertEquals(View.GONE, activity.findViewById<View>(R.id.loading).visibility)

		list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST))
		list.layout(0, 0, 1080, list.measuredHeight)
		list.findViewHolderForAdapterPosition(1)!!.itemView.findViewById<View>(R.id.row).performClick()

		val saved = File(root(), "B/pickme.txt")
		assertTrue(waitFor { saved.exists() && saved.length() > 0 })
		assertEquals("pick me", saved.readText())
		assertFalse(File(root(), "A/pickme.txt").exists())
	}

	@Test
	fun sharedImageStream_isCopied() {
		setDestinations("/Pictures")
		launch(streamIntent("image/jpeg", sourceFile("photo.jpg", "jpeg-bytes")))

		val saved = File(root(), "Pictures/photo.jpg")
		assertTrue(waitFor { saved.exists() && saved.length() > 0 })
		assertEquals("jpeg-bytes", saved.readText())
	}

	@Test
	fun sharedVideoAndDocumentStreams_areCopied() {
		setDestinations("/Media")
		launch(streamIntent("video/mp4", sourceFile("clip.mp4", "video")))
		assertTrue(waitFor { File(root(), "Media/clip.mp4").length() > 0 })

		launch(streamIntent("application/pdf", sourceFile("report.pdf", "pdf")))
		assertTrue(waitFor { File(root(), "Media/report.pdf").length() > 0 })
	}

	@Test
	fun sendMultiple_savesEveryStream() {
		setDestinations("/Batch")
		val intent = Intent(context, SaverActivity::class.java)
			.setAction(Intent.ACTION_SEND_MULTIPLE)
			.setType("image/*")
			.putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(
				sourceFile("one.jpg", "1"), sourceFile("two.jpg", "2")))
		launch(intent)

		assertTrue(waitFor { File(root(), "Batch/one.jpg").length() > 0 && File(root(), "Batch/two.jpg").length() > 0 })
		assertEquals("1", File(root(), "Batch/one.jpg").readText())
		assertEquals("2", File(root(), "Batch/two.jpg").readText())
	}

	@Test
	fun existingFile_overwriteStrategy_replacesFile() {
		setDestinations("/Saved")
		val existing = File(root(), "Saved/helloworld.txt").apply { writeText("old") }
		prefs.edit().putString("file_exists", "Overwrite").commit()

		launch(textIntent("hello world"))
		assertTrue(waitFor { existing.readText() == "hello world" })
	}

	@Test
	fun existingFile_doNotSaveStrategy_keepsFile() {
		setDestinations("/Saved")
		val existing = File(root(), "Saved/helloworld.txt").apply { writeText("old") }
		prefs.edit().putString("file_exists", "Nothing").commit()

		val controller = launch(textIntent("hello world"))
		assertTrue(waitFor { controller.get().isFinishing })
		assertEquals("old", existing.readText())
		assertEquals(1, File(root(), "Saved").listFiles()!!.size)
	}

	@Test
	fun existingFile_postfixStrategy_appendsNumber() {
		setDestinations("/Saved")
		val existing = File(root(), "Saved/helloworld.txt").apply { writeText("old") }
		prefs.edit().putString("file_exists", "Postfix").commit()

		launch(textIntent("hello world"))
		val numbered = File(root(), "Saved/helloworld.1.txt")
		assertTrue(waitFor { numbered.exists() && numbered.length() > 0 })
		assertEquals("hello world", numbered.readText())
		assertEquals("old", existing.readText())
	}

	@Test
	fun directShareExtra_savesToTargetWithoutSelector() {
		setDestinations("/A", "/B")
		val intent = textIntent("direct").putExtra(SaverActivity.DIRECT_SHARE_FOLDER, "/B")
		launch(intent)

		assertTrue(waitFor { File(root(), "B/direct.txt").length() > 0 })
		assertFalse(File(root(), "A/direct.txt").exists())
	}

	@Test
	fun sharingShortcut_savesToTargetWithoutSelector() {
		setDestinations("/A", "/B")
		val intent = textIntent("shortcut").putExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID, "folder:/A")
		launch(intent)

		assertTrue(waitFor { File(root(), "A/shortcut.txt").length() > 0 })
		assertFalse(File(root(), "B/shortcut.txt").exists())
	}

	@Test
	fun unsupportedContent_showsSupportInformation() {
		setDestinations("/A", "/B")
		val intent = Intent(context, SaverActivity::class.java)
			.setAction(Intent.ACTION_SEND)
			.setType("application/x-unknown")
		val activity = launch(intent).get()

		assertTrue(waitFor { activity.findViewById<View>(R.id.not_supported_wrapper).visibility == View.VISIBLE })
		assertEquals(View.GONE, activity.findViewById<View>(R.id.list).visibility)
	}

	@Test
	fun noDestinations_savesToDropFilerFolder() {
		LocationHelperAccess.writeEmptyList()
		val controller = launch(textIntent("nowhere"))
		val saved = File(root(), "DropFiler/nowhere.txt")
		assertTrue(waitFor { saved.length() > 0 })
		assertEquals("nowhere", saved.readText())
		assertTrue(waitFor { controller.get().isFinishing })
	}

	@Test
	fun cancel_closesSheetWithoutSaving() {
		setDestinations("/A", "/B")
		val activity = launch(textIntent("cancel me")).get()
		assertTrue(waitFor { activity.findViewById<View>(R.id.list).visibility == View.VISIBLE })

		activity.findViewById<View>(R.id.cancel).performClick()
		assertTrue(activity.isFinishing)
		assertFalse(File(root(), "A/cancelme.txt").exists())
		assertFalse(File(root(), "B/cancelme.txt").exists())
	}

	private object LocationHelperAccess {
		fun writeEmptyList() = fjm.sh.dropfiler.util.LocationHelper.saveFolderList(context, mutableListOf())
	}
}
