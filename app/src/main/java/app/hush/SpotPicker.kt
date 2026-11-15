package app.hush

import kotlin.math.abs

/**
 * Picks the default ghost spot for a focused field. The ghost never covers another input field, a button of the
 * app or the cursor line, and it covers text only when every free spot has text. Of the spots, it takes the one
 * with the lowest cost: empty space is best, then a spot on a part of a wide clickable row. Near the field is
 * better than far, and just above the field at its right end is best. The picker also leaves room for X and the
 * check beside the ghost, and keeps them off inputs, buttons and text when it can. This object has no Android
 * code, so JVM tests can check it. All values are pixels.
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
     * What HushService reads from the window of the focused field, other than the field: [inputs] are the other
     * editable fields, [buttons] the clickable and focusable views, and [labels] the text (or icons with a
     * description) that the user cannot tap. [cursorLine] is the text line with the cursor, from the left to the
     * right of the field, or null when it is not known.
     */
    data class Field(
        val bounds: Box,
        val cursorLine: Box?,
        val inputs: List<Box>,
        val buttons: List<Box>,
        val labels: List<Box>,
    )

    /** [screen] is the part of the screen below the status bar. */
    data class Scene(val screen: Box, val keyboardTop: Int?, val field: Field)

    /** True when the field is so tall that the ghost can go inside it, away from the cursor line. */
    fun isTall(field: Box, sizes: Sizes) = field.height >= TALL * 2 * sizes.ghost

    /** Returns the ghost center. */
    fun pick(scene: Scene, sizes: Sizes): Pair<Int, Int> {
        val screen = scene.screen
        val floor = (scene.keyboardTop ?: screen.bottom).coerceAtMost(screen.bottom) - sizes.space - sizes.ghost
        val ceiling = screen.top + sizes.space + sizes.ghost
        val focused = scene.field.bounds
        val field = focused.copy(bottom = minOf(focused.bottom, floor + sizes.ghost))
        // X and the check must fit on the screen beside the ghost.
        val reach = sizes.gap + sizes.button
        val low = screen.left + sizes.edge + reach
        val high = maxOf(low, screen.right - sizes.edge - reach)
        val preferredX = (field.right - sizes.ghost).coerceIn(low, high)
        val above = field.top - sizes.space - sizes.ghost
        val fallback = preferredX to above.coerceIn(ceiling, maxOf(ceiling, floor))

        // Views that hold the field, and very large views (lists, page containers), do not count.
        val screenArea = screen.area
        fun counts(box: Box) = box.width > 0 && box.height > 0 && !box.contains(focused)
        fun notHuge(box: Box) = box.area * 4 <= screenArea
        val inputs = scene.field.inputs.filter(::counts)
        val views = scene.field.buttons.filter { counts(it) && notHuge(it) }
        val labels = scene.field.labels.filter { counts(it) && notHuge(it) }
        // A wide clickable view is a list row: it stays usable when the ghost covers a part of it.
        val (rows, small) = views.partition { it.width * 2 > screen.width }
        val blocked = inputs + small

        val tall = isTall(field, sizes) && scene.field.cursorLine != null
        val ys = (generateSequence(floor) { it - sizes.step }.takeWhile { it >= ceiling } + above).distinct().toList()
        val xs = (generateSequence(high) { it - sizes.step }.takeWhile { it >= low } + preferredX).distinct().toList()

        // The best spot without text under the ghost, and the best spot of all.
        var clear: Pair<Int, Int>? = null
        var clearCost = Long.MAX_VALUE
        var best: Pair<Int, Int>? = null
        var bestCost = Long.MAX_VALUE
        for (y in ys) {
            if (y < ceiling || y > floor) continue
            for (x in xs) {
                val ghost = Box.around(x, y, sizes.ghost + sizes.space)
                if (blocked.any { it.overlaps(ghost) }) continue
                if (scene.field.cursorLine?.overlaps(ghost) == true) continue
                val inField = field.overlaps(ghost)
                if (inField && !(tall && field.contains(ghost))) continue
                var cost = abs(x - preferredX).toLong() + 2L * abs(y - above)
                if (inField) cost += INSIDE_COST * sizes.ghost
                val onText = labels.count { it.overlaps(ghost) }
                cost += onText * LABEL_COST * sizes.ghost
                cost += rows.count { it.overlaps(ghost) } * ROW_COST * sizes.ghost
                val cancel = Box.around(x - sizes.gap, y, sizes.button)
                val confirm = Box.around(x + sizes.gap, y, sizes.button)
                cost += blocked.count { it.overlaps(cancel) || it.overlaps(confirm) } * BUTTON_COST * sizes.ghost
                cost += labels.count { it.overlaps(cancel) || it.overlaps(confirm) } * LABEL_BESIDE_COST * sizes.ghost
                if (onText == 0 && cost < clearCost) {
                    clearCost = cost
                    clear = x to y
                }
                if (cost < bestCost) {
                    bestCost = cost
                    best = x to y
                }
            }
        }
        return clear ?: best ?: fallback
    }

    // A field is tall when it is this many ghosts high.
    private const val TALL = 3
    // Extra costs, in ghost half-sizes. Moving one ghost half-size up or down costs 2.
    // A spot inside a tall field:
    private const val INSIDE_COST = 4L
    // A text label under the ghost, when every free spot has text:
    private const val LABEL_COST = 5L
    // A part of a wide clickable row under the ghost:
    private const val ROW_COST = 1L
    // An input or a button under X or the check:
    private const val BUTTON_COST = 3L
    // A text label under X or the check:
    private const val LABEL_BESIDE_COST = 2L
}
