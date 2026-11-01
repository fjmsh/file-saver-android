package fjm.sh.dropfiler.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.RecyclerView
import com.hchen.himiuix.MiuixAppBar
import com.hchen.himiuix.dialog.MiuixAlertDialog
import com.hchen.himiuix.widget.MiuixEditText
import java.io.File
import fjm.sh.dropfiler.R.*
import fjm.sh.dropfiler.ui.CardItem
import fjm.sh.dropfiler.ui.CardListAdapter
import fjm.sh.dropfiler.ui.MiuixListFragment
import fjm.sh.dropfiler.util.DebugLogger
import fjm.sh.dropfiler.util.LocationHelper
import java.util.Locale

/**
 * An activity for selecting a folder in the file system.
 */
class FolderSelectActivity : BaseActivity(), MiuixListFragment.Host {

	companion object {
		const val FOLDER_SELECTED = "FolderSelected"

		private const val KEY_CURRENT = "current"
		private const val KEY_BACK = "back"
		private const val KEY_EMPTY = "empty"
		private const val STATE_PATH = "currentPath"
	}

	private lateinit var log: DebugLogger

	@Suppress("DEPRECATION")
	private var currentPath: String = Environment.getExternalStorageDirectory().absolutePath

	private lateinit var appBar: MiuixAppBar

	private var listView: RecyclerView? = null

	private var folderList: List<String> = ArrayList()

	private val adapter = CardListAdapter(onRowClick = { row, _ ->
		if (row.key == KEY_BACK) {
			val back = getBackLocation()
			if (back == null) {
				cancelIntent()
			} else {
				currentPath = back
			}
		} else {
			currentPath += row.key
		}
		updateListView()
		listView?.scrollToPosition(0)
	}, showDividers = true)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(layout.folder_select_activity)
		savedInstanceState?.getString(STATE_PATH)?.let { currentPath = it }

		log = DebugLogger(this)

		appBar = setUpAppBar(getString(string.title_activity_folder_select), showUp = true)

		// Init bottom buttons
		findViewById<View>(id.navigation_here).setOnClickListener {
			// Call back to previous activity with the location
			val intent = Intent()
			intent.putExtra(FOLDER_SELECTED, currentPath)
			setResult(RESULT_OK, intent)
			finish()
		}
		findViewById<View>(id.new_folder).setOnClickListener { showNewFolderDialog() }
		applyBottomInset(findViewById(id.navigation), asMargin = false)

		/**
		 * Goes up a directory, unless at the top, then exits
		 */
		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				val back = getBackLocation()
				if (back == null || currentPath == LocationHelper.rootLocation) {
					cancelIntent()
				} else {
					currentPath = back
					updateListView()
				}
			}
		})
	}

	override fun onListViewCreated(recyclerView: RecyclerView) {
		listView = recyclerView
		recyclerView.adapter = adapter
	}

	override fun onStart() {
		super.onStart()
		updateListView()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putString(STATE_PATH, currentPath)
	}

	/**
	 * Update the folder list view
	 */
	private fun updateListView() {
		log.v("Path is: $currentPath")

		val currentFile = File(currentPath)

		val fList = currentFile.listFiles()
				// Directories only
				?.filter { it.isDirectory }
				// Get the file path
				?.map { removeCurrent(it.absolutePath) }
				// Sort it
				?.sortedBy { it.lowercase(Locale.getDefault()) }
				// Convert to mutable
				?.toMutableList()
				// Default to empty
				?: ArrayList()

		folderList = fList.toList()
		log.d("List length: "+folderList.size)

		// Set title
		val relativePath = LocationHelper.removeRoot(currentPath)
		this.title = relativePath + File.separatorChar
		appBar.title = relativePath.substringAfterLast(File.separatorChar)
				.ifBlank { getString(string.folder_list_root_folder) }

		val items = mutableListOf<CardItem>(
			CardItem.Row(
				key = KEY_CURRENT,
				title = getString(string.folder_select_current),
				summary = this.title,
				icon = drawable.fs_ic_folder,
				clickable = false,
			),
			CardItem.Header(getString(string.title_activity_folder_select)),
			// Back button as first item
			CardItem.Row(
				key = KEY_BACK,
				title = getString(string.folder_select_up),
				icon = drawable.fs_ic_arrow_upward,
			),
		)
		folderList.forEach {
			val name = it.trimStart(File.separatorChar)
			items.add(CardItem.Row(
				key = it,
				title = name,
				showChevron = true,
				clickDescription = getString(string.folder_select_open_description, name),
			))
		}
		if (folderList.isEmpty()) {
			items.add(CardItem.Row(
				key = KEY_EMPTY,
				title = getString(string.folder_select_empty),
				icon = drawable.fs_ic_folder,
				clickable = false,
			))
		}
		adapter.items = items

		updateWritable(currentFile.canWrite())
	}

	/**
	 * Updates the menu items based on whether the directory is writable or not.
	 */
	private fun updateWritable(writable: Boolean){
		findViewById<View>(id.navigation_here).visibility = if (writable) View.VISIBLE else View.GONE
		findViewById<View>(id.navigation_read_only).visibility = if (writable) View.GONE else View.VISIBLE
		// Folders can only be created where the app can write
		findViewById<View>(id.new_folder).isEnabled = writable
	}

	/**
	 * Asks for a name and creates a new folder inside the current folder, then opens it.
	 */
	@SuppressLint("InflateParams")
	private fun showNewFolderDialog() {
		val view = layoutInflater.inflate(layout.fs_dialog_new_folder, null)
		val nameField = view.findViewById<MiuixEditText>(id.new_folder_name)
		val error = view.findViewById<TextView>(id.new_folder_error)
		nameField.setHint(string.folder_select_new_folder_hint)
		nameField.setAutoRequestFocus(true)
		nameField.getInnerEditText().apply {
			contentDescription = getString(string.folder_select_new_folder_hint)
			setSingleLine(true)
			imeOptions = EditorInfo.IME_ACTION_DONE
		}
		nameField.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {
				error.visibility = View.GONE
			}
		})

		MiuixAlertDialog(this)
			.setTitle(getString(string.folder_select_new_folder_title))
			.setCustomView(view)
			// Keep the dialog open while the name is invalid
			.setAutoDismiss(false)
			.setNegativeButton(getString(string.dialog_negative)) { dialog, _ -> dialog.dismiss() }
			.setPositiveButton(getString(string.folder_select_new_folder_create)) { dialog, _ ->
				when (val result = createFolder(nameField.text?.toString().orEmpty())) {
					null -> dialog.dismiss()
					else -> {
						error.text = getString(result)
						error.visibility = View.VISIBLE
					}
				}
			}
			.show()
	}

	/**
	 * Creates [name] in the current folder and opens it.
	 * @return null on success, otherwise the message explaining the problem.
	 */
	private fun createFolder(name: String): Int? {
		val trimmed = name.trim()
		val folder = File(currentPath, trimmed)
		return when {
			trimmed.isEmpty() -> string.folder_select_new_folder_error_empty
			trimmed == "." || trimmed == ".." || trimmed.contains(File.separatorChar) || trimmed.contains('\u0000') ->
				string.folder_select_new_folder_error_invalid
			folder.exists() -> string.folder_select_new_folder_error_exists
			!folder.mkdir() -> {
				log.w("Unable to create folder ${folder.absolutePath}")
				string.folder_select_new_folder_error_failed
			}
			else -> {
				log.i("Created folder ${folder.absolutePath}")
				currentPath = folder.absolutePath
				updateListView()
				listView?.scrollToPosition(0)
				null
			}
		}
	}

	/**
	 * Remove the current location from the given path.
	 */
	private fun removeCurrent(location: String): String =
		location.replace(currentPath, "")

	/**
	 * Cancels the current activity
	 */
	private fun cancelIntent() {
		val intent = Intent()
		intent.putExtra(FOLDER_SELECTED, currentPath)
		setResult(RESULT_CANCELED, intent)
		finish()
	}

	/**
	 * Returns the back location for the current path
	 */
	private fun getBackLocation(): String? =
		if (currentPath.contains(File.separatorChar)) {
			currentPath.substring(0, currentPath.lastIndexOf(File.separatorChar))
		} else {
			null
		}

}
