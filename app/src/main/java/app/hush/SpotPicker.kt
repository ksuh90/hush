package app.hush

import kotlin.math.abs

/**
 * Picks the default ghost spot for a focused field: a free place that covers no button of the app and not the
 * cursor line. It prefers the place just above the field at its right end, and it leaves room for X and the
 * check beside the ghost. This object has no Android code, so JVM tests can check it. All values are pixels.
 */
object SpotPicker {
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
        val area get() = width.toLong() * height

        fun overlaps(o: Box) = left < o.right && o.left < right && top < o.bottom && o.top < bottom

        fun contains(o: Box) = left <= o.left && top <= o.top && right >= o.right && bottom >= o.bottom

        companion object {
            fun around(x: Int, y: Int, half: Int) = Box(x - half, y - half, x + half, y + half)
        }
    }

    /**
     * [ghost]: half of the ghost with its ring. [button]: half of X or the check. [gap]: from the ghost center to
     * the center of X or the check. [space]: free space around the ghost. [edge]: from the screen edge to the
     * outer edge of a button. [step]: the distance between two spots that the picker tries.
     */
    data class Sizes(val ghost: Int, val button: Int, val gap: Int, val space: Int, val edge: Int, val step: Int)

    /**
     * [screen] is the part of the screen below the status bar. [cursorLine] is the text line with the cursor, from
     * the left to the right of the field, or null when it is not known. [buttons] are the bounds of the clickable
     * and focusable views in the window of the field, other than the field.
     */
    data class Scene(
        val screen: Box,
        val keyboardTop: Int?,
        val field: Box,
        val cursorLine: Box?,
        val buttons: List<Box>,
    )

    /** What HushService reads from the window of the focused field. See [Scene]. */
    data class Field(val bounds: Box, val cursorLine: Box?, val buttons: List<Box>)

    /** True when the field is so tall that the ghost can go inside it, away from the cursor line. */
    fun isTall(field: Box, sizes: Sizes) = field.height >= TALL * 2 * sizes.ghost

    /** Returns the ghost center. */
    fun pick(scene: Scene, sizes: Sizes): Pair<Int, Int> {
        val screen = scene.screen
        val floor = (scene.keyboardTop ?: screen.bottom).coerceAtMost(screen.bottom) - sizes.space - sizes.ghost
        val ceiling = screen.top + sizes.space + sizes.ghost
        val field = scene.field.copy(bottom = minOf(scene.field.bottom, floor + sizes.ghost))
        // X and the check must fit on the screen beside the ghost.
        val reach = sizes.gap + sizes.button
        val low = screen.left + sizes.edge + reach
        val high = maxOf(low, screen.right - sizes.edge - reach)
        val preferredX = (field.right - sizes.ghost).coerceIn(low, high)
        val above = field.top - sizes.space - sizes.ghost
        val fallback = preferredX to above.coerceIn(ceiling, maxOf(ceiling, floor))

        // Small views are buttons and the ghost must not cover them. Wide views (list rows, other fields) stay
        // usable when the ghost covers a part of them, so the ghost only moves a little to keep off them.
        // Very large views (lists, containers) do not count.
        val screenArea = screen.area
        val views = scene.buttons.filter { it.width > 0 && it.height > 0 && it.area * 4 <= screenArea && !it.contains(scene.field) }
        val (wide, small) = views.partition { it.width * 2 > screen.width }

        val rows = ArrayList<Pair<Int, Int>>() // y and its extra cost
        for (k in 0..OUTSIDE_ROWS) {
            rows += above - k * sizes.step to 0
            rows += field.bottom + sizes.space + sizes.ghost + k * sizes.step to 0
        }
        val inside = isTall(field, sizes) && scene.cursorLine != null
        if (inside) {
            var y = field.bottom - sizes.space - sizes.ghost
            while (y >= field.top + sizes.space + sizes.ghost) {
                rows += y to INSIDE_COST * sizes.ghost
                y -= sizes.step
            }
        }
        val columns = (generateSequence(high) { it - sizes.step }.takeWhile { it >= low } + preferredX).distinct().toList()

        var best: Pair<Int, Int>? = null
        var bestCost = Long.MAX_VALUE
        for ((y, extra) in rows) {
            if (y < ceiling || y > floor) continue
            for (x in columns) {
                val ghost = Box.around(x, y, sizes.ghost + sizes.space)
                if (small.any { it.overlaps(ghost) }) continue
                if (scene.cursorLine?.overlaps(ghost) == true) continue
                if (extra == 0 && field.overlaps(ghost)) continue
                var cost = abs(x - preferredX).toLong() + 2L * abs(y - above) + extra
                cost += wide.count { it.overlaps(ghost) } * WIDE_COST * sizes.ghost
                val cancel = Box.around(x - sizes.gap, y, sizes.button)
                val confirm = Box.around(x + sizes.gap, y, sizes.button)
                cost += small.count { it.overlaps(cancel) || it.overlaps(confirm) } * BUTTON_COST * sizes.ghost
                if (cost < bestCost) {
                    bestCost = cost
                    best = x to y
                }
            }
        }
        return best ?: fallback
    }

    // Rows above and below the field that the picker tries.
    private const val OUTSIDE_ROWS = 8
    // A field is tall when it is this many ghosts high.
    private const val TALL = 3
    // Extra costs, in ghost half-sizes: a spot inside a tall field, a wide view under the ghost,
    // and a button under X or the check.
    private const val INSIDE_COST = 4
    private const val WIDE_COST = 1L
    private const val BUTTON_COST = 3L
}
