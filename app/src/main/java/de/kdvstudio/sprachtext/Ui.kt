package de.kdvstudio.sprachtext

import android.content.Context
import android.content.res.Configuration
import android.widget.ScrollView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.isDark(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

class MaxHeightScrollView(ctx: Context, private val maxH: Int) : ScrollView(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
    }
}
