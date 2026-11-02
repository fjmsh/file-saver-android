package fjm.sh.dropfiler.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.io.File
import java.util.Locale

/**
 * Generates a round letter icon for a Direct Share target, using the first character after
 * the last slash. Replaces the unmaintained TextDrawable library (only published on JCenter).
 */
object DirectShareIcon {

	private const val BACKGROUND = 0xFFD3D3D3.toInt() // Color.LTGRAY, as before
	private const val FOREGROUND = 0xFFFFFFFF.toInt()

	fun letterFor(text: String): String {
		val index = text.indexOfLast { it == File.separatorChar }
		val char = if (index >= 0 && index + 1 < text.length) text[index + 1] else text.firstOrNull() ?: File.separatorChar
		return char.toString().uppercase(Locale.getDefault())
	}

	fun generate(context: Context, text: String, size: Int? = null): Bitmap {
		val width = size ?: context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
		val height = size ?: context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)
		val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(bitmap)

		val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		paint.color = BACKGROUND
		val radius = minOf(width, height) / 2f
		canvas.drawCircle(width / 2f, height / 2f, radius, paint)

		paint.color = FOREGROUND
		paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
		paint.textAlign = Paint.Align.CENTER
		paint.textSize = radius
		val baseline = height / 2f - (paint.descent() + paint.ascent()) / 2f
		canvas.drawText(letterFor(text), width / 2f, baseline, paint)

		return bitmap
	}
}
