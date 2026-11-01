package fjm.sh.dropfiler.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import fjm.sh.dropfiler.R
import fjm.sh.dropfiler.util.LocationHelper
import android.provider.OpenableColumns
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fjm.sh.dropfiler.util.DebugLogger
import java.io.*
import android.widget.TextView
import fjm.sh.dropfiler.saver.LocationSelectTask
import fjm.sh.dropfiler.saver.HandleSingleTextTask
import fjm.sh.dropfiler.data.Pair
import fjm.sh.dropfiler.ui.CardItem
import fjm.sh.dropfiler.ui.CardListAdapter
import fjm.sh.dropfiler.util.DirectShareHelper
import fjm.sh.dropfiler.util.PreferenceHelper
import fjm.sh.dropfiler.util.SaveNotifier
import java.net.MalformedURLException
import java.net.URL
import java.util.Locale

/**
 * An activity to handle saving files.
 * https://developer.android.com/training/sharing/receive.html
 */
class SaverActivity : BaseActivity() {

	companion object {
		const val FILENAME_REGEX = "[^-_.A-Za-z0-9]"
		const val FILENAME_LENIENT_REGEX = "[\\p{Cntrl}]"
		const val FILENAME_LENGTH_LIMIT = 100

		const val FILENAME_EXT_MATCH_LIMIT = 1000
		const val DIRECT_SHARE_FOLDER = "DIRECT_SHARE_FOLDER"

		private const val KEY_LOCATION_PREFIX = "location:"
		private const val KEY_PICKER = "picker"
		private const val KEY_MISSING = "missing"
	}

	val requestCodeLocationSelect = 1

	private lateinit var log: DebugLogger
	private val preferenceHelper = PreferenceHelper(this)

	var location: String? = null
	var convertedMime: String? = null

	var debugInfo: MutableList<Pair> = mutableListOf()

	/** Names of the files being saved, for the result notification. */
	private val savedFileNames: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

	lateinit var returnFromActivityResult: (uri: Uri?) -> Unit

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.saver_activity)
		log = DebugLogger(this)
		setUpSheet()

		preferenceHelper.loadPreferences()

		if (isOpenWith) {
			// "Open with": make sure the file can actually be read before offering destinations.
			// Opening may be slow (e.g. cloud providers), so it is not done on the UI thread.
			Thread {
				val readable = canReadOpenWithSource()
				runOnUiThread {
					if (isFinishing || isDestroyed) return@runOnUiThread
					if (readable) {
						chooseDestination()
					} else {
						finishIntent(false, R.string.toast_open_with_unreadable)
					}
				}
			}.start()
			return
		}
		chooseDestination()
	}

	/** Whether this activity was started with "Open with" (ACTION_VIEW) rather than Share. */
	private val isOpenWith: Boolean
		get() = Intent.ACTION_VIEW == intent?.action

	/**
	 * Whether the file given to "Open with" can be read. Fails for a missing URI, an
	 * unsupported scheme, a missing or revoked read grant, directories and providers that
	 * refuse access. The stream is only opened and closed again; nothing is read or changed.
	 */
	private fun canReadOpenWithSource(): Boolean {
		val uri = intent?.data ?: return false.also { log.w("Open with: no URI") }
		if (uri.scheme != ContentResolver.SCHEME_CONTENT && uri.scheme != ContentResolver.SCHEME_FILE) {
			log.w("Open with: unsupported scheme ${uri.scheme}")
			return false
		}
		return try {
			contentResolver.openInputStream(uri)?.use { true } ?: false
		} catch (e: FileNotFoundException) {
			log.w("Open with: cannot open $uri: ${e.message}")
			false
		} catch (e: IOException) {
			log.w("Open with: cannot open $uri: ${e.message}")
			false
		} catch (e: SecurityException) {
			log.w("Open with: no permission to read $uri: ${e.message}")
			false
		} catch (e: RuntimeException) {
			// e.g. IllegalArgumentException / IllegalStateException from a misbehaving provider
			log.w("Open with: provider failed for $uri: ${e.message}")
			false
		}
	}

	/**
	 * Display name of a content URI, falling back to the last path segment.
	 */
	private fun displayName(uri: Uri): String? {
		if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
			try {
				contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
					val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
					if (nameIndex >= 0 && it.moveToFirst()) {
						it.getString(nameIndex)?.takeIf { name -> name.isNotBlank() }?.let { name -> return name }
					}
				}
			} catch (e: RuntimeException) {
				// SecurityException / IllegalArgumentException from the provider; fall back below
				log.w("Unable to query display name of $uri: ${e.message}")
			}
		}
		return uri.lastPathSegment?.substringAfterLast(File.separatorChar)?.takeIf { it.isNotBlank() }
	}

	/**
	 * MIME type of the "Open with" file: from the intent, then the provider, then the file
	 * extension, otherwise a generic binary type.
	 */
	private fun openWithMime(uri: Uri): String =
		intent.type?.takeIf { it.isNotBlank() }
			?: try { contentResolver.getType(uri) } catch (e: RuntimeException) { null }
			?: displayName(uri)?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }?.let {
				MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase(Locale.ROOT))
			}
			?: "application/octet-stream"

	/**
	 * Use the Direct Share destination, or offer the destination list.
	 */
	private fun chooseDestination() {
		val directShareLocation = intent?.extras?.getString(DIRECT_SHARE_FOLDER)
				// Sharing shortcut (Direct Share on Android 10+)
				?: DirectShareHelper.folderForShortcut(this, intent?.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID))

		when {
			directShareLocation != null -> {
				location = LocationHelper.addRoot(directShareLocation)
				if (isAvailable(location!!)) {
					useIntent({ finishIntent(it) })
				} else {
					// The Direct Share folder was moved or deleted; let the user pick another
					askForAnotherFolder(location!!)
				}
			}
			PreferenceHelper.forceSaving -> loadList()
			else -> {
				useIntent({ success ->
					log.i("Supported: $success")
					// Success should never be null on a dryRun
					if (success!!){
						loadList()
					} else {
						showNotSupported()
					}
				}, dryRun=true)
			}
		}
	}

	/**
	 * Prepare the bottom sheet presentation. Tapping outside the sheet or Cancel closes it
	 * without saving, exactly like pressing back.
	 */
	private fun setUpSheet() {
		val sheet = findViewById<View>(R.id.sheet)
		applyBottomInset(sheet, asMargin = false)

		// Limit the width on tablets and in landscape
		val maxWidth = resources.getDimensionPixelSize(R.dimen.fs_sheet_max_width)
		if (resources.displayMetrics.widthPixels > maxWidth) {
			sheet.layoutParams = sheet.layoutParams.apply { width = maxWidth }
		}

		findViewById<View>(R.id.scrim).setOnClickListener { finish() }
		findViewById<View>(R.id.cancel).setOnClickListener { finish() }
		findViewById<TextView>(R.id.sheet_subtitle).text = describeSharedContent()

		// Slide in. Uses the animator duration scale, so it is instant when animations are off.
		sheet.post {
			sheet.translationY = sheet.height.toFloat()
			sheet.animate().translationY(0f)
				.setDuration(resources.getInteger(android.R.integer.config_mediumAnimTime).toLong())
				.setInterpolator(android.view.animation.DecelerateInterpolator(2f))
				.start()
		}
	}

	/**
	 * A short description of what is being shared, e.g. "3 items" or "Link".
	 */
	private fun describeSharedContent(): CharSequence? = try {
		@Suppress("DEPRECATION")
		when {
			isOpenWith -> intent.data?.let { displayName(it) }
					?: resources.getQuantityString(R.plurals.saver_subtitle_files, 1, 1)
			Intent.ACTION_SEND_MULTIPLE == intent.action ->
				intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.size?.let {
					resources.getQuantityString(R.plurals.saver_subtitle_files, it, it)
				}
			intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) != null ->
				intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uri ->
					uri.lastPathSegment?.substringAfterLast(File.separatorChar)
				} ?: resources.getQuantityString(R.plurals.saver_subtitle_files, 1, 1)
			intent.getStringExtra(Intent.EXTRA_TEXT) != null -> {
				val text = intent.getStringExtra(Intent.EXTRA_TEXT)!!
				if (isUrl(text)) getString(R.string.saver_subtitle_link) else getString(R.string.saver_subtitle_text)
			}
			else -> intent.type
		}
	} catch (e: RuntimeException) {
		// Malformed extras from the sharing app; the description is only cosmetic
		null
	}

	private fun isUrl(text: String): Boolean = try {
		URL(text)
		true
	} catch (e: MalformedURLException) {
		false
	}

	private fun destinationRow(location: String, position: Int): CardItem.Row =
		if (PreferenceHelper.locationSelectEnabled && position == 0) {
			CardItem.Row(
				key = KEY_LOCATION_PREFIX + position,
				title = getString(R.string.location_select_title),
				summary = getString(R.string.location_select_summary),
				icon = R.drawable.fs_ic_document,
				showChevron = true,
			)
		} else {
			val name = location.substringAfterLast(File.separatorChar)
					.ifBlank { getString(R.string.folder_list_root_folder) }
			val path = if (location.isBlank()) File.separator else location
			val available = isAvailable(LocationHelper.addRoot(location))
			CardItem.Row(
				key = KEY_LOCATION_PREFIX + position,
				title = name,
				// Folders deleted or moved elsewhere cannot be chosen
				summary = if (available) path else getString(R.string.saver_folder_missing, path),
				clickable = available,
				enabled = available,
			)
		}

	/**
	 * Whether a destination folder still exists. Destinations can be deleted or moved by other
	 * apps after they were added; saving into a missing folder would fail.
	 */
	private fun isAvailable(absoluteLocation: String): Boolean =
		// The built-in DropFiler folder is created when it is first needed
		LocationHelper.prepareDestination(absoluteLocation)

	/**
	 * Shows the destination list so the user can pick one.
	 * @param locations Stored locations; the first is "location select" when that is enabled.
	 * @param missingLocation A destination that no longer exists, explained at the top.
	 */
	private fun showDestinations(locations: List<String>, missingLocation: String? = null) {
		runOnUiThread {
			findViewById<View>(R.id.loading).visibility = View.GONE
			// Init list view
			val listView = findViewById<RecyclerView>(R.id.list)
			listView.visibility = View.VISIBLE
			listView.layoutManager = LinearLayoutManager(this)

			val items = mutableListOf<CardItem>()
			missingLocation?.let {
				items.add(CardItem.Row(
					key = KEY_MISSING,
					title = getString(R.string.saver_missing_title),
					summary = getString(R.string.saver_missing_body, displayPath(it)),
					icon = R.drawable.fs_ic_error,
					clickable = false,
					danger = true,
				))
				items.add(CardItem.Header(getString(R.string.saver_missing_choose)))
			}
			locations.forEachIndexed { position, it -> items.add(destinationRow(it, position)) }
			if (missingLocation != null && !PreferenceHelper.locationSelectEnabled) {
				// Always offer the system picker, even if no other destination is available
				items.add(CardItem.Row(
					key = KEY_PICKER,
					title = getString(R.string.saver_choose_other_folder),
					summary = getString(R.string.location_select_summary),
					icon = R.drawable.fs_ic_document,
					showChevron = true,
				))
			}

			var selected = false
			listView.adapter = CardListAdapter(onRowClick = { row, _ ->
				// Ignore repeated taps while the first selection is being saved
				if (!selected) {
					val position = row.key.removePrefix(KEY_LOCATION_PREFIX).toIntOrNull()
					when {
						row.key == KEY_PICKER -> {
							// No location: the system file picker (location select) is used
							location = null
						}
						position == null -> return@CardListAdapter
						PreferenceHelper.locationSelectEnabled && position == 0 -> {
							// The first item is the location select
							location = null
						}
						else -> {
							location = LocationHelper.addRoot(locations[position])
							if (!isAvailable(location!!)) return@CardListAdapter
						}
					}
					selected = true
					useIntent({ finishIntent(it) })
				}
			}, showDividers = true).apply {
				this.items = items
			}
			listView.scrollToPosition(0)
		}
	}

	/**
	 * The destination that should have been used no longer exists: explain it and show the
	 * remaining destinations and the system picker instead of failing.
	 */
	private fun askForAnotherFolder(missingAbsoluteLocation: String) {
		log.w("Destination $missingAbsoluteLocation does not exist")
		location = null
		val locations = LocationHelper.loadFolderListWLocationSelect(this)
		showDestinations(locations, missingLocation = missingAbsoluteLocation)
	}

	private fun displayPath(absoluteLocation: String): String =
		LocationHelper.removeRoot(absoluteLocation).ifBlank { File.separator }

	/**
	 * Load the list of locations
	 */
	private fun loadList() {
		// Never empty: the built-in DropFiler folder is used when there is nothing else
		val locations = LocationHelper.loadFolderListWLocationSelect(this)
		if (locations.size > 1) {
			showDestinations(locations)
			return // await selection
		}
		// Only one location, just use it
		if (!PreferenceHelper.locationSelectEnabled) {
			// Only set location when not using location select
			location = LocationHelper.addRoot(locations[0])
			if (!isAvailable(location!!)) {
				// The only destination was moved or deleted (or the DropFiler folder could
				// not be created); let the user pick another
				askForAnotherFolder(location!!)
				return // await selection
			}
		}
		useIntent({ finishIntent(it) })
	}

	private fun useIntent(callback: (success: Boolean?) -> Unit, dryRun: Boolean = false) {
		// Get intent action and MIME type
		val action: String? = intent.action
		val type: String? = intent.type

		log.i("Action: $action")
		log.i("Type: $type")

		if (Intent.ACTION_VIEW == action) {
			// "Open with": the file is the intent data, and the type may be missing
			return handleView(callback, dryRun)
		}

		type?.lowercase(Locale.getDefault())?.let {
			if (Intent.ACTION_SEND == action) {
				return handleSingle(callback, dryRun)
			} else if (Intent.ACTION_SEND_MULTIPLE == action) {
				return handleMultiple(callback, dryRun)
			}

			if (PreferenceHelper.forceSaving) {
				// Save the file the best way we can
				return handleSingle(callback, dryRun)
			}
		}

		log.i("No supporting method")

		// Failed to reach callback
		finishIntent(false)
	}

	/**
	 * Show the not supported information.
	 */
	private fun showNotSupported() {
		// Hide list
		runOnUiThread {
			findViewById<View>(R.id.loading).visibility = View.GONE
			findViewById<View>(R.id.list).visibility = View.GONE
			// Generate issue text here as should always be English and does not need to be in strings.xml
			val bobTitle = StringBuilder()
			bobTitle.append("Support Request - ")
			bobTitle.append(intent.type)
			val bobBody = StringBuilder()
			bobBody.append("Support request. Generated by DropFiler.%0D%0A")
			bobBody.append("%0D%0AIntent type: ")
			bobBody.append(intent.type)
			bobBody.append("%0D%0AIntent action: ")
			bobBody.append(intent.action)
			intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
				bobBody.append("%0D%0AText: ")
				bobBody.append(it)
			}
			intent.getStringExtra(Intent.EXTRA_SUBJECT)?.let {
				bobBody.append("%0D%0ASubject: ")
				bobBody.append(it)
			}
			debugInfo.forEach {
				bobBody.append("%0D%0A${it.key}: ")
				bobBody.append(it.value)
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
				intent.getStringExtra(Intent.EXTRA_HTML_TEXT)?.let {
					bobBody.append("%0D%0AHTML Text: ")
					bobBody.append(it)
				}
			}
			// Version
			try {
				val versionName = packageManager.getPackageInfo(packageName, 0).versionName
				bobBody.append("%0D%0AApplication Version: ")
				bobBody.append(versionName)
			} catch (e: PackageManager.NameNotFoundException) {
				log.e("Unable to get package version", e)
			}
			bobBody.append("%0D%0A%0D%0AMore information: TYPE_ADDITIONAL_INFORMATION_HERE")
			bobBody.append("%0D%0A%0D%0AThank you")
			val issueLink = getString(R.string.issues_new_url) + "?title=" +
					bobTitle.toString().replace(" ", "%20") +
					"&body=" +
					bobBody.toString().replace(" ", "%20").replace("=", "%3D")
			log.i(issueLink)

			// Build and show unsupported message
			val supportView = findViewById<TextView>(R.id.not_supported)
			@Suppress("DEPRECATION")
			supportView.text = Html.fromHtml(resources.getString(R.string.not_supported, issueLink))
			supportView.movementMethod = LinkMovementMethod.getInstance()
			findViewById<View>(R.id.not_supported_wrapper).visibility = View.VISIBLE
		}
	}

	/**
	 * Call when the intent is finished
	 */
	private fun finishIntent(success: Boolean?, messageId: Int? = null) {
		// Notify user
		runOnUiThread {
			when {
				messageId != null -> Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show()
				success == null -> Toast.makeText(this, R.string.toast_save_in_progress, Toast.LENGTH_SHORT).show()
				success -> Toast.makeText(this, R.string.toast_save_successful, Toast.LENGTH_SHORT).show()
				else -> Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
			}
		}

		// Result notification. In progress (null) means DownloadManager is saving a URL;
		// DownloadBroadcastReceiver posts the result when the download completes.
		val names = savedFileNames.toList()
		when {
			success == null -> Unit
			success && messageId == null -> SaveNotifier.notifySaved(applicationContext, names, location)
			else -> SaveNotifier.notifyFailed(applicationContext, messageId?.let { getString(it) }, names)
		}

		exitApplication()
	}

	/**
	 * Exists the application is the best way available for the Android version
	 */
	@SuppressLint("NewApi")
	private fun exitApplication() {
		when {
			Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> finishAndRemoveTask()
			Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN -> finishAffinity()
			else -> finish()
		}
	}

	/**
	 * Handle the saving of single items.
	 */
	private fun handleSingle(callback: (success: Boolean?) -> Unit, dryRun: Boolean) {
		// Try save stream first
		@Suppress("DEPRECATION")
		intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let {
			log.d("Text has stream")
			getFilename(it, intent.type ?: "", dryRun) {filename ->
				saveUri(it, filename, callback, dryRun)
			}
			return
		}

		// Save the text
		intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
			log.d("Text Extra: $it")
			HandleSingleTextTask(this, it, intent, dryRun, callback).execute()
		} ?: callback(false)
	}

	/**
	 * Handle "Open with" (ACTION_VIEW): save a copy of the single file in the intent data,
	 * through the same filename, duplicate handling and save path as a shared file.
	 */
	private fun handleView(callback: (success: Boolean?) -> Unit, dryRun: Boolean) {
		val uri = intent.data ?: return callback(false)
		getFilename(uri, openWithMime(uri), dryRun) { filename ->
			saveUri(uri, filename, callback, dryRun)
		}
	}

	/**
	 * Handle the saving of multiple streams.
	 */
	private fun handleMultiple(callback: (success: Boolean?) -> Unit, dryRun: Boolean) {
		@Suppress("DEPRECATION")
		val imageUris: ArrayList<Uri>? = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
		imageUris?.let {
			var counter = 0
			var completeSuccess = true
			imageUris.forEach {uri ->
				getFilename(uri, intent.type ?: "", dryRun) { filename ->
					saveUri(uri, filename, { success ->
						counter++
						success?.let {
							completeSuccess = completeSuccess && it
						}
						if (counter == imageUris.size){
							callback(completeSuccess)
						}
					}, dryRun)
				}
			}
		} ?: callback(false)
	}

	/**
	 * Save the given uri to filesystem.
	 */
	private fun saveUri(uri: Uri, filename: String, callback: (success: Boolean?) -> Unit, dryRun: Boolean) {
		val destinationFilename = LocationHelper.safeAddPath(location, filename)

		if (!dryRun) {
			val sourceFilename = uri.path
			log.d("Saving $sourceFilename to $destinationFilename")
		} else {
			// This method can be skipped when doing a dry run
			return callback(true)
		}

		if (location == null){
			// No location, use location select
			returnFromActivityResult = {
				if (it == null){
					callback(false)
				} else {
					val pfd = contentResolver.openFileDescriptor(it, "w")
					if (pfd == null){
						callback(false)
					} else {
						val bos = BufferedOutputStream(FileOutputStream(pfd.fileDescriptor))
						try {
							contentResolver.openInputStream(uri)?.use { bis ->
								saveStream(bis, bos, null, callback, dryRun)
							} ?: callback(false)
						} catch (e: IOException) {
							log.e("Unable to read $uri", e)
							callback(false)
						} catch (e: SecurityException) {
							// e.g. the read grant of an "Open with" file was revoked
							log.e("No permission to read $uri", e)
							callback(false)
						}
					}
				}
			}
			LocationSelectTask(this).save(filename, convertedMime!!)
		} else {
			try {
				contentResolver.openInputStream(uri)?.use { bis ->
					val fout = File(destinationFilename)
					if (!fout.exists()) {
						fout.createNewFile()
					}
					val bos = BufferedOutputStream(FileOutputStream(fout, false))
					saveStream(bis, bos, destinationFilename, callback, dryRun)
				} ?: callback(false)
			} catch (e: FileNotFoundException) {
				log.e("File not found. Perhaps you are overriding the same file and just deleted it?", e)
				callback(false)
			} catch (e: IOException) {
				// e.g. the destination folder was deleted after it was checked
				log.e("Unable to create file $destinationFilename", e)
				callback(false)
			} catch (e: SecurityException) {
				// e.g. the read grant of an "Open with" file was revoked
				log.e("No permission to read $uri", e)
				callback(false)
			}
		}
	}

	/**
	 * Save a stream to the filesystem.
	 */
	fun saveStream(bis: InputStream, bos: OutputStream, destinationFilename: String?,
						   callback: (success: Boolean?) -> Unit, dryRun: Boolean) {
		if (dryRun){
			// This entire method can be skipped when doing a dry run
			return callback(true)
		}

		var success = false

		try {
			val buf = ByteArray(1024)
			var bytesRead = bis.read(buf)
			while (bytesRead != -1) {
				bos.write(buf, 0, bytesRead)
				bytesRead = bis.read(buf)
			}

			// Done
			success = true

			if (PreferenceHelper.registerMediaServer && destinationFilename != null){
				MediaScannerConnection.scanFile(this, arrayOf(destinationFilename), null, null)
			}
		} catch (e: IOException) {
			log.e("Unable to save file", e)
		} finally {
			try {
				bos.close()
			} catch (e: IOException) {
				log.e("Unable to close stream", e)
			}
		}
		callback(success)
	}

	/**
	 * Get the filename from a Uri.
	 */
	private fun getFilename(uri: Uri, mime: String, dryRun: Boolean, callback: (filename: String) -> Unit) {
		// Find the actual filename
		if (uri.scheme == "content") {
			try {
				contentResolver.query(uri, null, null, null, null)
			} catch (e: RuntimeException) {
				// e.g. SecurityException: the provider refuses queries; use the URI instead
				log.w("Unable to query $uri: ${e.message}")
				null
			}?.use {
				val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
				if (nameIndex >= 0 && it.moveToFirst() && !it.isNull(nameIndex)) {
					return getFilename(it.getString(nameIndex), mime, dryRun, callback, uri)
				}
			}
		}
		getFilename(uri.lastPathSegment ?: "default", mime, dryRun, callback, uri)
	}

	/**
	 * Get the filename from a string.
	 */
	fun getFilename(s: String, mime: String, dryRun: Boolean, callback: (filename: String) -> Unit, uri: Uri? = null) {
		// Validate the mime type
		log.d("Converting mime: $mime")
		convertedMime = mime.replaceAfter(";", "").replace(";", "")
		log.d("Converted mime: $convertedMime")

		log.d("Converting filename: $s")

		var result = s
				// Take last section after a slash (excluding the slash)
				.replaceBeforeLast("/", "").replace("/", "")
				// Remove non-filename characters
				.replace(Regex(if (PreferenceHelper.useLenientRegex) FILENAME_LENIENT_REGEX else FILENAME_REGEX), "")
				// Trim whitespace
				.trim()

		if (result.length > FILENAME_LENGTH_LIMIT) {
			// Do not go over the filename length limit
			result = result.substring(0, FILENAME_LENGTH_LIMIT)
		}

		var ext = result.substringAfterLast('.', "")
		if (!MimeTypeMap.getSingleton().hasExtension(ext)){
			// Add file extension
			MimeTypeMap.getSingleton().getExtensionFromMimeType(convertedMime)?.let {
				ext = it
				log.d("Adding extension $it to $result")
				result += ".$it"
			}
		}

		log.d("Converted filename: $result")

		if (!dryRun) {
			location?.let { location ->
				val destinationFilename = LocationHelper.safeAddPath(location, result)
				val f = File(destinationFilename)
				if (f.exists()) {
					when (PreferenceHelper.saveStrategy) {
						0 -> {
							// Overwrite. Delete the file, so that it will be overridden
							uri?.let { u ->
								val sourceFilename = u.path
								if (sourceFilename?.contains(destinationFilename) == true) {
									log.w("Aborting! It appears you are saving the file over itself")
									finishIntent(false, R.string.toast_save_file_exists_self_abort)
									return
								}
							}
							runOnUiThread {
								Toast.makeText(this, R.string.toast_save_file_exists_overwrite, Toast.LENGTH_SHORT).show()
							}
							log.w("Overwriting $result")
							f.delete()
						}
						1 -> {
							// Nothing. Quit
							log.d("Quitting due to duplicate $result")
							finishIntent(false, R.string.toast_save_file_exists_fail)
							return
						}
						2 -> {
							// Postfix. Add counter before extension
							log.d("Adding postfix to $result")
							var i = 1
							val before = destinationFilename.substringBeforeLast('.', "") + "."
							if (ext.isNotBlank()) {
								ext = ".$ext"
							}
							while (File(before + i + ext).exists()) {
								i++
								if (i > FILENAME_EXT_MATCH_LIMIT) {
									// We have a lot of matches. This is too hard
									log.w("There are over $FILENAME_EXT_MATCH_LIMIT matches for $before$ext. Aborting.")
									finishIntent(false, R.string.toast_save_file_exists_fail)
									return
								}
							}
							// Update the result to contain the new number
							result = result.substringBeforeLast('.', "") + "." + i + ext
						}
						3 -> {
							// Request
							log.e("Not implemented!")
							throw NotImplementedError("Requesting filename not yet implemented.")
						}
					}
				}
			}
		}

		if (!dryRun) {
			savedFileNames.add(result)
		}
		callback(result)
	}

	fun failOnActivityResult(requestCode: Int) {
		onActivityResult(requestCode, Activity.RESULT_CANCELED, null)
	}

	/**
	 * Return from location select
	 */
	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)

		when (requestCode){
			requestCodeLocationSelect -> {
				if (resultCode == RESULT_OK){
					data?.data?.let {uri ->
						return returnFromActivityResult(uri)
					}
				} else {
					// Selection cancelled, fail save
					returnFromActivityResult(null)
				}
			}
		}
	}

}
