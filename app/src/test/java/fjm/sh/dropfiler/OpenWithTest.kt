package fjm.sh.dropfiler

import android.app.Notification
import android.app.NotificationManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import fjm.sh.dropfiler.TestSupport.context
import fjm.sh.dropfiler.TestSupport.prefs
import fjm.sh.dropfiler.TestSupport.root
import fjm.sh.dropfiler.TestSupport.setDestinations
import fjm.sh.dropfiler.TestSupport.waitFor
import fjm.sh.dropfiler.activity.SaverActivity
import fjm.sh.dropfiler.ui.CardItem
import fjm.sh.dropfiler.ui.CardListAdapter
import org.junit.Assert.assertArrayEquals
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
import java.io.FileNotFoundException
import kotlin.random.Random

/**
 * "Open with DropFiler" (ACTION_VIEW): DropFiler is offered for files and saves a copy
 * through the same destination sheet and save path as Share.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [23, 28, 29, 34, 36])
class OpenWithTest {

	/** A file provider like the ones file managers expose, with failure modes. */
	class TestProvider : ContentProvider() {
		class Entry(
			val file: File?,
			val displayName: String?,
			val type: String?,
			val error: Exception? = null,
		)

		companion object {
			const val AUTHORITY = "fjm.sh.dropfiler.test.files"
			val entries = mutableMapOf<String, Entry>()
		}

		private fun entry(uri: Uri) = entries[uri.lastPathSegment] ?: throw FileNotFoundException(uri.toString())

		override fun onCreate() = true
		override fun getType(uri: Uri): String? = entries[uri.lastPathSegment]?.type

		override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
						   selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
			val entry = entry(uri)
			(entry.error as? SecurityException)?.let { throw it }
			val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
			if (entry.displayName != null) {
				cursor.addRow(arrayOf(entry.displayName, entry.file?.length()))
			}
			return cursor
		}

		override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
			val entry = entry(uri)
			entry.error?.let { throw it }
			require(mode == "r") { "The source must only be opened for reading, not '$mode'" }
			return ParcelFileDescriptor.open(entry.file, ParcelFileDescriptor.MODE_READ_ONLY)
		}

		override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
		override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
			throw UnsupportedOperationException()
		override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
			throw UnsupportedOperationException()
	}

	@Before
	fun setUp() {
		root().listFiles()?.forEach { it.deleteRecursively() }
		prefs.edit().clear().commit()
		// These tests are about the user's own destinations: hide the built-in DropFiler
		// folder, which is then only used when there is no other destination (DefaultFolderTest)
		prefs.edit().putBoolean(fjm.sh.dropfiler.util.LocationHelper.PREF_HIDE_DEFAULT_FOLDER, true).commit()
		TestProvider.entries.clear()
		Robolectric.setupContentProvider(TestProvider::class.java, TestProvider.AUTHORITY)
		shadowOf(MimeTypeMap.getSingleton()).apply {
			addExtensionMimeTypeMapping("txt", "text/plain")
			addExtensionMimeTypeMapping("jpg", "image/jpeg")
			addExtensionMimeTypeMapping("mp4", "video/mp4")
			addExtensionMimeTypeMapping("pdf", "application/pdf")
			addExtensionMimeTypeMapping("zip", "application/zip")
			addExtensionMimeTypeMapping("apk", "application/vnd.android.package-archive")
		}
	}

	private fun source(name: String, bytes: ByteArray): File =
		File(context.cacheDir, "src-${Random.nextInt(1_000_000)}-$name").apply { writeBytes(bytes) }

	/** Publishes a file through [TestProvider] and returns its content:// URI. */
	private fun provide(id: String, file: File?, displayName: String?, type: String?, error: Exception? = null): Uri {
		TestProvider.entries[id] = TestProvider.Entry(file, displayName, type, error)
		return Uri.parse("content://${TestProvider.AUTHORITY}/document/$id")
	}

	private fun viewIntent(uri: Uri?, type: String?) = Intent(context, SaverActivity::class.java)
		.setAction(Intent.ACTION_VIEW)
		.apply { if (type != null) setDataAndType(uri, type) else data = uri }
		.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

	private fun launch(intent: Intent): ActivityController<SaverActivity> =
		Robolectric.buildActivity(SaverActivity::class.java, intent).setup()

	private fun notifications() =
		shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).allNotifications

	private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()
	private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

	private fun rows(activity: SaverActivity) =
		(activity.findViewById<RecyclerView>(R.id.list).adapter as CardListAdapter).items.filterIsInstance<CardItem.Row>()

	private fun clickRow(activity: SaverActivity, title: String) {
		val list = activity.findViewById<RecyclerView>(R.id.list)
		val adapter = list.adapter as CardListAdapter
		val position = adapter.items.indexOfFirst { it is CardItem.Row && it.title == title }
		list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.AT_MOST))
		list.layout(0, 0, 1080, list.measuredHeight)
		list.findViewHolderForAdapterPosition(position)!!.itemView.findViewById<View>(R.id.row).performClick()
	}

	private fun assertFailedWithUnreadable(controller: ActivityController<SaverActivity>) {
		assertTrue(waitFor { controller.get().isFinishing })
		assertTrue(waitFor { notifications().isNotEmpty() })
		val notification = notifications().single()
		assertEquals(context.getString(R.string.notification_failed_title), notification.title())
		assertEquals(context.getString(R.string.toast_open_with_unreadable), notification.text())
	}

	private fun savedFiles(): List<File> = root().walkTopDown().filter { it.isFile }.toList()

	// Intent resolution -------------------------------------------------------------------

	private fun resolvesToSaver(intent: Intent): Boolean =
		context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
			.any { it.activityInfo.name == SaverActivity::class.java.name }

	@Test
	fun implicitViewIntent_resolvesForContentUrisOfAnyType() {
		val uri = Uri.parse("content://com.example.files/document/42")
		listOf("application/pdf", "image/jpeg", "video/mp4", "application/zip",
			"application/octet-stream", "text/plain", "application/vnd.android.package-archive",
			"application/x-nonstandard").forEach { type ->
			assertTrue("VIEW $type", resolvesToSaver(Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)))
		}
	}

	@Test
	fun implicitViewIntent_resolvesForFileUris() {
		val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///sdcard/Download/a.pdf"), "application/pdf")
		assertTrue(resolvesToSaver(intent))
	}

	@Test
	fun webLinks_areNotHandled() {
		assertFalse(resolvesToSaver(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/file.pdf"))))
		assertFalse(resolvesToSaver(Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com/"))))
		assertFalse(resolvesToSaver(Intent(Intent.ACTION_VIEW)
			.setDataAndType(Uri.parse("https://example.com/file.pdf"), "application/pdf")))
		assertFalse(resolvesToSaver(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"))
			.addCategory(Intent.CATEGORY_BROWSABLE)))
	}

	@Test
	fun otherDocumentContracts_areNotDeclared() {
		val uri = Uri.parse("content://com.example.files/document/42")
		listOf(Intent.ACTION_EDIT, Intent.ACTION_GET_CONTENT, Intent.ACTION_OPEN_DOCUMENT).forEach { action ->
			assertFalse(action, resolvesToSaver(Intent(action).setDataAndType(uri, "application/pdf")))
		}
	}

	@Test
	fun shareTargets_stillResolve() {
		assertTrue(resolvesToSaver(Intent(Intent.ACTION_SEND).setType("image/jpeg")))
		assertTrue(resolvesToSaver(Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")))
	}

	// Intake -------------------------------------------------------------------------------

	@Test
	fun openWith_singleDestination_savesCopyWithDisplayName() {
		setDestinations("/Docs")
		val bytes = Random.nextBytes(4096)
		val file = source("report.pdf", bytes)
		val controller = launch(viewIntent(provide("7", file, "Quarterly report.pdf", "application/pdf"), "application/pdf"))

		val saved = File(root(), "Docs/Quarterlyreport.pdf")
		assertTrue(waitFor { saved.length() == bytes.size.toLong() })
		assertArrayEquals(bytes, saved.readBytes())
		// The source is copied, never changed or removed
		assertArrayEquals(bytes, file.readBytes())
		assertTrue(waitFor { controller.get().isFinishing })
		assertEquals(context.getString(R.string.notification_saved_title), notifications().single().title())
	}

	@Test
	fun openWith_multipleDestinations_showsSheet_andSavesToChosenFolder() {
		setDestinations("/A", "/B")
		val bytes = "photo".toByteArray()
		val activity = launch(viewIntent(provide("9", source("p.jpg", bytes), "holiday.jpg", "image/jpeg"), "image/jpeg")).get()

		assertTrue(waitFor { activity.findViewById<View>(R.id.list).visibility == View.VISIBLE })
		assertEquals("holiday.jpg", activity.findViewById<TextView>(R.id.sheet_subtitle).text.toString())
		assertEquals(listOf("A", "B"), rows(activity).map { it.title.toString() })

		clickRow(activity, "B")
		assertTrue(waitFor { File(root(), "B/holiday.jpg").length() > 0 })
		assertArrayEquals(bytes, File(root(), "B/holiday.jpg").readBytes())
		assertFalse(File(root(), "A/holiday.jpg").exists())
	}

	@Test
	fun openWith_cancel_savesNothing_andLeavesSourceUnchanged() {
		setDestinations("/A", "/B")
		val file = source("c.txt", "keep".toByteArray())
		val activity = launch(viewIntent(provide("c", file, "c.txt", "text/plain"), "text/plain")).get()
		assertTrue(waitFor { activity.findViewById<View>(R.id.list).visibility == View.VISIBLE })

		activity.findViewById<View>(R.id.cancel).performClick()
		assertTrue(activity.isFinishing)
		assertTrue(savedFiles().isEmpty())
		assertEquals("keep", file.readText())
	}

	@Test
	fun openWith_variousTypes_areCopiedByteForByte() {
		setDestinations("/In")
		listOf(
			Triple("archive.zip", "application/zip", "z"),
			Triple("app.apk", "application/vnd.android.package-archive", "k"),
			Triple("clip.mp4", "video/mp4", "v"),
			Triple("notes.txt", "text/plain", "t"),
			Triple("blob.weird", "application/x-nonstandard", "w"),
		).forEach { (name, type, id) ->
			val bytes = Random.nextBytes(1024)
			launch(viewIntent(provide(id, source(name, bytes), name, type), type))
			val saved = File(root(), "In/$name")
			assertTrue(name, waitFor { saved.length() == bytes.size.toLong() })
			assertArrayEquals(name, bytes, saved.readBytes())
		}
	}

	@Test
	fun openWith_largeFile_isCopiedCompletely() {
		setDestinations("/Big")
		val bytes = Random.nextBytes(6 * 1024 * 1024 + 123)
		launch(viewIntent(provide("big", source("big.bin", bytes), "big.bin", "application/octet-stream"), "application/octet-stream"))
		val saved = File(root(), "Big/big.bin")
		assertTrue(waitFor(timeoutMs = 30_000) { saved.length() == bytes.size.toLong() })
		assertArrayEquals(bytes, saved.readBytes())
	}

	@Test
	fun openWith_missingMimeAndExtension_stillSaves() {
		setDestinations("/In")
		// No type in the intent, none from the provider, and no extension in the name
		launch(viewIntent(provide("readme", source("README", "plain".toByteArray()), "README", null), null))
		assertTrue(waitFor { savedFiles().isNotEmpty() })
		val saved = savedFiles().single()
		assertTrue(saved.name, saved.name.startsWith("README"))
		assertEquals("plain", saved.readText())
	}

	@Test
	fun openWith_typeFromProvider_whenIntentHasNone() {
		setDestinations("/In")
		launch(viewIntent(provide("noext", source("scan", "pdf".toByteArray()), "scan", "application/pdf"), null))
		val saved = File(root(), "In/scan.pdf")
		assertTrue(waitFor { saved.length() > 0 })
	}

	@Test
	fun openWith_noDisplayName_usesUriName() {
		setDestinations("/In")
		launch(viewIntent(provide("photo.jpg", source("x.jpg", "j".toByteArray()), null, "image/jpeg"), "image/jpeg"))
		assertTrue(waitFor { File(root(), "In/photo.jpg").length() > 0 })
	}

	@Test
	fun openWith_repeatedInvocations_followDuplicateSetting() {
		setDestinations("/In")
		prefs.edit().putString("file_exists", "Postfix").commit()
		repeat(3) { i ->
			val uri = provide("r$i", source("same.txt", "v$i".toByteArray()), "same.txt", "text/plain")
			val controller = launch(viewIntent(uri, "text/plain"))
			assertTrue(waitFor { controller.get().isFinishing })
		}
		assertEquals("v0", File(root(), "In/same.txt").readText())
		assertEquals("v1", File(root(), "In/same.1.txt").readText())
		assertEquals("v2", File(root(), "In/same.2.txt").readText())
	}

	@Test
	fun openWith_fileUri_isCopied() {
		setDestinations("/In")
		val file = source("legacy.txt", "legacy".toByteArray())
		launch(viewIntent(Uri.fromFile(file), "text/plain"))
		val saved = savedFiles().single()
		assertTrue(saved.name.endsWith("legacy.txt"))
		assertEquals("legacy", saved.readText())
		assertEquals("legacy", file.readText())
	}

	// Failures -----------------------------------------------------------------------------

	@Test
	fun openWith_nullUri_failsWithoutCrashing() {
		setDestinations("/In")
		assertFailedWithUnreadable(launch(viewIntent(null, "application/pdf")))
		assertTrue(savedFiles().isEmpty())
	}

	@Test
	fun openWith_revokedGrant_failsWithoutSaving() {
		setDestinations("/In")
		val uri = provide("denied", source("d.pdf", "x".toByteArray()), "d.pdf", "application/pdf",
			SecurityException("Permission Denial: reading requires grantUriPermission"))
		assertFailedWithUnreadable(launch(viewIntent(uri, "application/pdf")))
		assertTrue(savedFiles().isEmpty())
	}

	@Test
	fun openWith_missingFile_failsWithoutSaving() {
		setDestinations("/In")
		val uri = provide("gone", null, "gone.pdf", "application/pdf", FileNotFoundException("deleted"))
		assertFailedWithUnreadable(launch(viewIntent(uri, "application/pdf")))
		assertTrue(savedFiles().isEmpty())
	}

	@Test
	fun openWith_failingProvider_failsWithoutSaving() {
		// (An unregistered authority cannot be modelled here: Robolectric returns a placeholder
		// stream where Android throws FileNotFoundException, which is covered above.)
		setDestinations("/In")
		val uri = provide("broken", null, "b.pdf", "application/pdf", IllegalStateException("provider crashed"))
		assertFailedWithUnreadable(launch(viewIntent(uri, "application/pdf")))
		assertTrue(savedFiles().isEmpty())
	}

	@Test
	fun openWith_directory_failsWithoutSaving() {
		setDestinations("/In")
		val dir = File(context.cacheDir, "a-folder").apply { mkdirs() }
		assertFailedWithUnreadable(launch(viewIntent(Uri.fromFile(dir), "*/*")))
		assertTrue(savedFiles().isEmpty())
	}

	@Test
	fun openWith_webUrlSentExplicitly_isRejected() {
		setDestinations("/In")
		assertFailedWithUnreadable(launch(viewIntent(Uri.parse("https://example.com/a.pdf"), "application/pdf")))
		assertTrue(savedFiles().isEmpty())
	}
}
