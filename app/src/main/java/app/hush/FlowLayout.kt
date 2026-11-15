package app.hush

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/** Puts the children in rows, from the start, and starts a new row when a child does not fit. */
class FlowLayout(context: Context, attrs: AttributeSet?) : ViewGroup(context, attrs) {
    private val gap = (GAP_DP * resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val inner = width - paddingLeft - paddingRight
        val spec = MeasureSpec.makeMeasureSpec(inner, MeasureSpec.AT_MOST)
        for (i in 0 until childCount) getChildAt(i).measure(spec, MeasureSpec.UNSPECIFIED)
        val height = flow(inner) { _, _, _ -> }
        setMeasuredDimension(width, if (childCount == 0) 0 else paddingTop + height + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        flow(width - paddingLeft - paddingRight) { child, x, y ->
            val left = if (rtl) width - paddingRight - x - child.measuredWidth else paddingLeft + x
            child.layout(left, paddingTop + y, left + child.measuredWidth, paddingTop + y + child.measuredHeight)
        }
    }

    /** Calls [place] with the position of each child in a row of [inner] pixels, and returns the height of all rows. */
    private fun flow(inner: Int, place: (View, Int, Int) -> Unit): Int {
        var x = 0
        var y = 0
        var rowHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (x > 0 && x + child.measuredWidth > inner) {
                x = 0
                y += rowHeight
                rowHeight = 0
            }
            place(child, x, y)
            x += child.measuredWidth + gap
            rowHeight = maxOf(rowHeight, child.measuredHeight)
        }
        return y + rowHeight
    }

    private companion object {
        const val GAP_DP = 8
    }
}
