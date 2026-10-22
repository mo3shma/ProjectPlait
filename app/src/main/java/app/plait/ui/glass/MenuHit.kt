package app.plait.ui.glass

import kotlin.math.floor

/** Finger y -> menu row. [firstRowTop] is where row 0 starts, in the same coordinates as [y]; -1 when the finger is off every row. */
fun menuIndexAt(y: Float, firstRowTop: Float, rowHeight: Float, count: Int): Int {
    if (rowHeight <= 0f || y < firstRowTop) return -1
    val i = floor((y - firstRowTop) / rowHeight).toInt()
    return if (i in 0 until count) i else -1
}
