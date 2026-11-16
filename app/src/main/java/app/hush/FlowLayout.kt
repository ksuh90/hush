package app.hush

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * Puts the children in rows, from the start, and starts a new row when a child does not fit. With [maxRows], only
 * the children in the first rows show: the others get no size, so they do not draw, take no touch and are not
 * visible to TalkBack.
 */
class FlowLayout(context: Context, attrs: AttributeSet?) : ViewGroup(context, attrs) {
    private val gap = (GAP_DP * resources.displayMetrics.density).toInt()

    /** The most rows that show, or 0 for all rows. */
    var maxRows = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    /** The number of rows that all the children need, from the last measure. */
    var rowCount = 0
        private set

    /** The number of children that show, from the last measure. */
    var shownCount = 0
        private set

    /** Called after a layout that changed the number of children, [rowCount] or [shownCount]. */
    var onFlowChange: (() -> Unit)? = null
    private var reportedChildren = -1
    private var reportedRows = -1
    private var reportedShown = -1

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val inner = width - paddingLeft - paddingRight
        val spec = MeasureSpec.makeMeasureSpec(inner, MeasureSpec.AT_MOST)
        for (i in 0 until childCount) getChildAt(i).measure(spec, MeasureSpec.UNSPECIFIED)
        val height = flow(inner) { _, _, _ -> }
        setMeasuredDimension(width, if (shownCount == 0) 0 else paddingTop + height + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        flow(width - paddingLeft - paddingRight) { i, x, y ->
            val child = getChildAt(i)
            val left = if (rtl) width - paddingRight - x - child.measuredWidth else paddingLeft + x
            child.layout(left, paddingTop + y, left + child.measuredWidth, paddingTop + y + child.measuredHeight)
        }
        for (i in shownCount until childCount) getChildAt(i).layout(0, 0, 0, 0)
        if (childCount != reportedChildren || rowCount != reportedRows || shownCount != reportedShown) {
            reportedChildren = childCount
            reportedRows = rowCount
            reportedShown = shownCount
            // The listener can change other views, so it runs after this layout pass.
            post { onFlowChange?.invoke() }
        }
    }

    /** Places the children in rows of [inner] pixels, sets [rowCount] and [shownCount], and returns the height. */
    private fun flow(inner: Int, place: (Int, Int, Int) -> Unit): Int {
        val flow = flow(
            childCount, inner, gap, maxRows, { getChildAt(it).measuredWidth }, { getChildAt(it).measuredHeight }, place,
        )
        rowCount = flow.rows
        shownCount = flow.shown
        return flow.height
    }

    /** Where the children went: the [rows] that all children need, the [shown] children and the [height] they take. */
    data class Flow(val rows: Int, val shown: Int, val height: Int)

    companion object {
        private const val GAP_DP = 8

        /**
         * Puts [count] children of [width] and [height] in rows of [inner] pixels, with [gap] between the children
         * of a row. A child starts a new row when it does not fit, and a child that is wider than a row has a row of
         * its own. Calls [place] with the index, x and y of each child in the first [maxRows] rows (all rows for 0).
         */
        fun flow(
            count: Int,
            inner: Int,
            gap: Int,
            maxRows: Int,
            width: (Int) -> Int,
            height: (Int) -> Int,
            place: (Int, Int, Int) -> Unit,
        ): Flow {
            var x = 0
            var y = 0
            var rowHeight = 0
            var rows = 0
            var shown = 0
            var shownHeight = 0
            for (i in 0 until count) {
                if (rows == 0 || x > 0 && x + width(i) > inner) {
                    if (rows > 0) {
                        x = 0
                        y += rowHeight
                        rowHeight = 0
                    }
                    rows++
                }
                rowHeight = maxOf(rowHeight, height(i))
                if (maxRows == 0 || rows <= maxRows) {
                    place(i, x, y)
                    shown++
                    shownHeight = y + rowHeight
                }
                x += width(i) + gap
            }
            return Flow(rows, shown, shownHeight)
        }
    }
}
