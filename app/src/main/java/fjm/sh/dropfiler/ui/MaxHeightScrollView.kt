package fjm.sh.dropfiler.ui

import android.content.Context
import android.util.AttributeSet
import androidx.core.widget.NestedScrollView

/**
 * A NestedScrollView that never grows taller than [maxHeightFraction] of the screen.
 * Used for long dialog content so that the dialog buttons stay visible.
 */
class MaxHeightScrollView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
) : NestedScrollView(context, attrs, defStyleAttr) {

	var maxHeightFraction = 0.5f

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val maxHeight = (resources.displayMetrics.heightPixels * maxHeightFraction).toInt()
		super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST))
	}
}
