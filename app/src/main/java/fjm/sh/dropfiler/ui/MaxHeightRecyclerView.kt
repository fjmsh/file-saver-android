package fjm.sh.dropfiler.ui

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

/**
 * A RecyclerView that wraps its content but never grows taller than [maxHeightFraction]
 * of the screen, so the share sheet keeps its title and cancel button visible.
 */
class MaxHeightRecyclerView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {

	var maxHeightFraction = 0.6f

	override fun onMeasure(widthSpec: Int, heightSpec: Int) {
		val maxHeight = (resources.displayMetrics.heightPixels * maxHeightFraction).toInt()
		super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST))
	}
}
