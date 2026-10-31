package fjm.sh.dropfiler.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.hchen.himiuix.helper.ShadowHelper
import com.hchen.himiuix.widget.MiuixCardView
import fjm.sh.dropfiler.R
import com.hchen.himiuix.R as MiuixR

/**
 * Items shown by [CardListAdapter].
 */
sealed class CardItem {

	/** A section title, rendered with the Miuix divider style. */
	data class Header(val title: CharSequence) : CardItem()

	/**
	 * A row inside a rounded group card. Consecutive rows form one group.
	 * @param key Opaque value passed back to click listeners.
	 */
	data class Row(
		val key: String,
		val title: CharSequence,
		val summary: CharSequence? = null,
		@DrawableRes val icon: Int = R.drawable.fs_ic_folder,
		val clickable: Boolean = true,
		val showChevron: Boolean = false,
		val actionIcon: Int = 0,
		val actionDescription: CharSequence? = null,
		val clickDescription: CharSequence? = null,
		/** Shown dimmed and announced as disabled, e.g. a destination that no longer exists. */
		val enabled: Boolean = true,
		/** Warning style (red icon), used for error notices. */
		val danger: Boolean = false,
		/** Can be reordered: shows a drag handle and "Move up / down" accessibility actions. */
		val draggable: Boolean = false,
	) : CardItem()

	/** An informational card, used for empty states. */
	data class Message(
		@DrawableRes val icon: Int,
		val title: CharSequence,
		val body: CharSequence,
		val actionText: CharSequence? = null,
	) : CardItem()
}

/**
 * RecyclerView adapter rendering Miuix styled grouped cards.
 */
class CardListAdapter(
	private val onRowClick: (CardItem.Row, Int) -> Unit = { _, _ -> },
	private val onRowAction: (CardItem.Row, Int) -> Unit = { _, _ -> },
	private val onMessageAction: () -> Unit = {},
	/** Called when the user starts dragging a [CardItem.Row.draggable] row. */
	private val onStartDrag: (RecyclerView.ViewHolder) -> Unit = {},
	/** Accessibility "Move up" (-1) / "Move down" (+1) for draggable rows. */
	private val onMoveBy: (CardItem.Row, Int) -> Unit = { _, _ -> },
	/** Draw a muted divider between consecutive rows of a group (destination lists). */
	private val showDividers: Boolean = false,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	companion object {
		private const val TYPE_HEADER = 0
		private const val TYPE_ROW = 1
		private const val TYPE_MESSAGE = 2
	}

	var items: List<CardItem> = emptyList()
		@SuppressLint("NotifyDataSetChanged")
		set(value) {
			field = value.toMutableList()
			// Lists are short; group corner radii depend on neighbours, so rebind everything.
			notifyDataSetChanged()
		}

	/**
	 * Moves an item while it is being dragged. Corner radii are refreshed by
	 * [refreshAfterDrag] when the drag ends.
	 */
	fun moveItem(from: Int, to: Int) {
		val list = items as MutableList<CardItem>
		list.add(to, list.removeAt(from))
		notifyItemMoved(from, to)
	}

	/** Rebinds all rows so that the group corners match the new order. */
	@SuppressLint("NotifyDataSetChanged")
	fun refreshAfterDrag() = notifyDataSetChanged()

	override fun getItemCount(): Int = items.size

	override fun getItemViewType(position: Int): Int = when (items[position]) {
		is CardItem.Header -> TYPE_HEADER
		is CardItem.Row -> TYPE_ROW
		is CardItem.Message -> TYPE_MESSAGE
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		return when (viewType) {
			TYPE_HEADER -> HeaderHolder(inflater.inflate(R.layout.fs_item_header, parent, false))
			TYPE_MESSAGE -> MessageHolder(inflater.inflate(R.layout.fs_item_message, parent, false))
			else -> RowHolder(inflater.inflate(R.layout.fs_item_row, parent, false))
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (val item = items[position]) {
			is CardItem.Header -> (holder as HeaderHolder).bind(item)
			is CardItem.Row -> (holder as RowHolder).bind(item, position)
			is CardItem.Message -> (holder as MessageHolder).bind(item)
		}
	}

	private inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
		private val title = view as TextView

		init {
			ViewCompat.setAccessibilityHeading(title, true)
		}

		fun bind(item: CardItem.Header) {
			title.text = item.title
		}
	}

	private inner class MessageHolder(view: View) : RecyclerView.ViewHolder(view) {
		private val icon = view.findViewById<ImageView>(R.id.message_icon)
		private val title = view.findViewById<TextView>(R.id.message_title)
		private val body = view.findViewById<TextView>(R.id.message_body)
		private val action = view.findViewById<Button>(R.id.message_action)

		init {
			ViewCompat.setAccessibilityHeading(title, true)
			action.setOnClickListener { onMessageAction() }
		}

		fun bind(item: CardItem.Message) {
			icon.setImageResource(item.icon)
			title.text = item.title
			body.text = item.body
			action.text = item.actionText
			action.visibility = if (item.actionText == null) View.GONE else View.VISIBLE
		}
	}

	private inner class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
		private val card = view as MiuixCardView
		private val row = view.findViewById<View>(R.id.row)
		private val iconTile = view.findViewById<View>(R.id.icon_tile)
		private val icon = view.findViewById<ImageView>(R.id.icon)
		private val title = view.findViewById<TextView>(R.id.title)
		private val summary = view.findViewById<TextView>(R.id.summary)
		private val chevron = view.findViewById<View>(R.id.chevron)
		private val action = view.findViewById<ImageButton>(R.id.action)
		private val dragHandle = view.findViewById<View>(R.id.drag_handle)
		private val divider = view.findViewById<View>(R.id.row_divider)
		private val radius = view.resources.getDimension(MiuixR.dimen.miuix_prefs_card_radius)

		// Miuix press feedback (background darkens/lightens like Miuix preferences)
		private val shadowHelper = ShadowHelper.init(row)

		init {
			// Title and summary are read together as one item by screen readers
			ViewCompat.setScreenReaderFocusable(row, true)
			@SuppressLint("ClickableViewAccessibility")
			row.setOnTouchListener { _, event ->
				if (row.isClickable) shadowHelper.onTouchEvent(event)
				false
			}
			row.setOnClickListener {
				val position = bindingAdapterPosition
				(items.getOrNull(position) as? CardItem.Row)?.let { onRowClick(it, position) }
			}
			action.setOnClickListener {
				val position = bindingAdapterPosition
				(items.getOrNull(position) as? CardItem.Row)?.let { onRowAction(it, position) }
			}
			// Drag immediately from the handle, or long-press anywhere on the row
			@SuppressLint("ClickableViewAccessibility")
			dragHandle.setOnTouchListener { _, event ->
				if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN && isDraggable()) {
					onStartDrag(this)
				}
				false
			}
			row.setOnLongClickListener {
				if (isDraggable()) {
					onStartDrag(this)
					true
				} else false
			}
		}

		private fun isDraggable() = (items.getOrNull(bindingAdapterPosition) as? CardItem.Row)?.draggable == true

		private val moveActionIds = mutableListOf<Int>()

		/** "Move up" / "Move down" for screen reader and switch access users. */
		private fun bindMoveActions(item: CardItem.Row, position: Int) {
			moveActionIds.forEach { ViewCompat.removeAccessibilityAction(row, it) }
			moveActionIds.clear()
			if (!item.draggable) return
			val context = row.context
			fun add(label: Int, delta: Int) {
				moveActionIds.add(ViewCompat.addAccessibilityAction(row, context.getString(label)) { _, _ ->
					(items.getOrNull(bindingAdapterPosition) as? CardItem.Row)?.let { onMoveBy(it, delta) }
					true
				})
			}
			if ((items.getOrNull(position - 1) as? CardItem.Row)?.draggable == true) add(R.string.folder_list_move_up, -1)
			if ((items.getOrNull(position + 1) as? CardItem.Row)?.draggable == true) add(R.string.folder_list_move_down, 1)
		}

		fun bind(item: CardItem.Row, position: Int) {
			icon.setImageResource(item.icon)
			title.text = item.title
			summary.text = item.summary
			summary.visibility = if (item.summary.isNullOrEmpty()) View.GONE else View.VISIBLE
			chevron.visibility = if (item.showChevron) View.VISIBLE else View.GONE
			dragHandle.visibility = if (item.draggable) View.VISIBLE else View.GONE
			row.isLongClickable = item.draggable
			bindMoveActions(item, position)

			// Disabled rows are dimmed; screen readers announce them as disabled
			row.isEnabled = item.enabled
			// Dim the content only, so the card background stays solid
			val alpha = if (item.enabled) 1f else 0.45f
			for (i in 0 until (row as ViewGroup).childCount) row.getChildAt(i).alpha = alpha
			iconTile.setBackgroundResource(if (item.danger) R.drawable.fs_icon_tile_danger else R.drawable.fs_icon_tile)
			ImageViewCompat.setImageTintList(icon, if (item.danger) {
				ColorStateList.valueOf(ContextCompat.getColor(icon.context, R.color.fs_danger))
			} else {
				val value = TypedValue()
				icon.context.theme.resolveAttribute(R.attr.fsColorOnAccentContainer, value, true)
				ColorStateList.valueOf(value.data)
			})
			// Notices need their full text; paths are kept short
			summary.maxLines = if (item.danger) 4 else 2
			summary.ellipsize = if (item.danger) TextUtils.TruncateAt.END else TextUtils.TruncateAt.MIDDLE

			row.isClickable = item.clickable
			row.isFocusable = item.clickable
			shadowHelper.setEnabled(item.clickable)
			if (item.clickable && item.clickDescription != null) {
				// Announce what the row does, e.g. "Double tap to open DCIM"
				ViewCompat.replaceAccessibilityAction(row, AccessibilityActionCompat.ACTION_CLICK, item.clickDescription, null)
			}

			if (item.actionIcon != 0) {
				action.visibility = View.VISIBLE
				action.setImageResource(item.actionIcon)
				action.contentDescription = item.actionDescription
			} else {
				action.visibility = View.GONE
			}

			// Round the outer corners of each group of consecutive rows
			val first = items.getOrNull(position - 1) !is CardItem.Row
			val last = items.getOrNull(position + 1) !is CardItem.Row
			divider.visibility = if (showDividers && !first) View.VISIBLE else View.GONE
			card.setTlRadius(if (first) radius else 0f).setTrRadius(if (first) radius else 0f)
				.setBlRadius(if (last) radius else 0f).setBrRadius(if (last) radius else 0f)
			card.invalidate()
			(card.layoutParams as? RecyclerView.LayoutParams)?.let {
				it.bottomMargin = if (last) card.resources.getDimensionPixelSize(MiuixR.dimen.miuix_basic_margin) else 0
				card.layoutParams = it
			}
		}
	}
}
