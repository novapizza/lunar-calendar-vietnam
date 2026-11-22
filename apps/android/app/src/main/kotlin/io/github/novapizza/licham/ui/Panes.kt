package io.github.novapizza.licham.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Window layouts, as the web app's modes:
 *  - phone: < 600 dp and no fold. One column, bottom tab bar.
 *  - book: an unfolded foldable whose fold runs top to bottom (Pixel Fold, Galaxy Z Fold inner
 *    screen). Two panes that meet at the hinge, each laid out like a phone; bottom tab bar.
 *  - wide: every other window from 600 dp. Left rail, two panes, tablet type scale.
 * The web can only guess a foldable from the window's size and shape (600-839 px, roughly
 * square). Here Jetpack WindowManager reports the fold itself (FoldingFeature), with its
 * position in the window, so book applies exactly when the window has a fold across it.
 */

/** A fold running top to bottom across the window: its left and right edges, px in window coordinates (equal for a seamless fold). */
@Immutable
data class Hinge(val left: Float, val right: Float)

/** The window's vertical fold, or null (no fold, a fold running left to right, or the app in a window the fold does not cross). */
val LocalHinge = compositionLocalOf<Hinge?> { null }

/**
 * The height the left pane's first item may take so that the pane does not scroll (the pane's
 * height less its top and bottom padding), or null outside the left pane of TwoPanes. The Today
 * page's tear-off sheet shrinks its number to fit it, so the date stays put and only the right
 * pane scrolls.
 */
val LocalPaneHeight = compositionLocalOf<Dp?> { null }

/**
 * Inside FlowPanes: the width of its left column, so an item across both columns can keep text
 * in the left pane (clear of the hinge). Null elsewhere.
 */
val LocalLeftColumn = compositionLocalOf<Dp?> { null }

/** Content columns of the two panes, px from the start of a box [width] px wide. */
@Immutable
private data class Columns(val width: Int, val height: Int, val leftStart: Int, val leftEnd: Int, val rightStart: Int, val rightEnd: Int) {
    /** Where one pane ends and the other begins (the hinge's middle in book). */
    val split get() = (leftEnd + rightStart) / 2
}

/**
 * Book: split at the hinge; 16 dp from the window edges and 8 dp either side of the hinge (the
 * web's px-4 and gap-4). Wide: split by [leftWeight]:[rightWeight]; 32 dp from the edges, 12 dp
 * either side of the split (unchanged from the two Pages in a Row it replaces).
 */
private fun Density.columns(width: Int, height: Int, originX: Float, hinge: Hinge?, leftWeight: Float, rightWeight: Float): Columns {
    if (hinge != null) {
        val outer = 16.dp.roundToPx()
        val inner = 8.dp.roundToPx()
        val left = (hinge.left - originX).roundToInt().coerceIn(0, width)
        val right = (hinge.right - originX).roundToInt().coerceIn(left, width)
        return Columns(width, height, outer, left - inner, right + inner, width - outer)
    }
    val outer = 32.dp.roundToPx()
    val inner = 12.dp.roundToPx()
    val split = (width * leftWeight / (leftWeight + rightWeight)).roundToInt()
    return Columns(width, height, outer, split - inner, split + inner, width - outer)
}

/** The box's x in the window, for placing the hinge; 0 until the first placement (where a book window's content starts anyway). */
@Composable
private fun PaneBox(content: @Composable (Columns) -> Unit, leftWeight: Float, rightWeight: Float) {
    val hinge = LocalHinge.current
    var originX by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().onPlaced { originX = it.positionInWindow().x }) {
        val cols = with(LocalDensity.current) { columns(constraints.maxWidth, constraints.maxHeight, originX, hinge, leftWeight, rightWeight) }
        content(cols)
    }
}

/**
 * Two panes side by side, each scrolling on its own. In book the left pane ends and the right
 * one begins at the hinge, whatever the weights.
 */
@Composable
fun TwoPanes(
    leftWeight: Float,
    rightWeight: Float,
    top: Dp,
    bottom: Dp,
    gap: Dp,
    rightTop: Dp = top,
    leftBottom: Dp = bottom,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) = PaneBox({ cols ->
    with(LocalDensity.current) {
        Row(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalPaneHeight provides cols.height.toDp() - top - leftBottom) {
                Page(
                    Modifier.width(cols.split.toDp()).fillMaxHeight(),
                    padding = PaddingValues(start = cols.leftStart.toDp(), end = (cols.split - cols.leftEnd).toDp(), top = top, bottom = leftBottom),
                    gap = gap, content = left,
                )
            }
            Page(
                Modifier.weight(1f).fillMaxHeight(),
                padding = PaddingValues(start = (cols.rightStart - cols.split).toDp(), end = (cols.width - cols.rightEnd).toDp(), top = rightTop, bottom = bottom),
                gap = gap, content = right,
            )
        }
    }
}, leftWeight, rightWeight)

/**
 * Items in reading order in a two-column grid over the panes' columns, filled row by row (left,
 * then right), one page that scrolls as a whole: as a year calendar's month cards, and a list
 * that grows only at the bottom, so loading more never moves what is on screen. The first
 * [spanning] items run across both columns above the grid. Each row is as tall as its taller
 * item. Like the web's Events grid.
 */
@Composable
fun FlowPanes(
    top: Dp,
    bottom: Dp,
    gap: Dp,
    spanning: Int = 0,
    scroll: ScrollState = rememberScrollState(),
    content: @Composable () -> Unit,
) = PaneBox({ cols ->
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = bottom)) {
        val leftColumn = with(LocalDensity.current) { (cols.leftEnd - cols.leftStart).toDp() }
        CompositionLocalProvider(LocalLeftColumn provides leftColumn) {
        Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
            val gapPx = gap.roundToPx()
            val span = min(spanning, measurables.size)
            val spanWidth = cols.rightEnd - cols.leftStart
            val leftWidth = cols.leftEnd - cols.leftStart
            val rightWidth = cols.rightEnd - cols.rightStart
            val placeables = measurables.mapIndexed { i, m ->
                val w = when {
                    i < span -> spanWidth
                    (i - span) % 2 == 0 -> leftWidth
                    else -> rightWidth
                }
                m.measure(Constraints.fixedWidth(w))
            }
            val positions = ArrayList<Pair<Int, Int>>(placeables.size)
            var y = 0
            for (i in 0 until span) {
                if (i > 0) y += gapPx
                positions += cols.leftStart to y
                y += placeables[i].height
            }
            var i = span
            while (i < placeables.size) {
                if (i > 0) y += gapPx
                positions += cols.leftStart to y
                var rowHeight = placeables[i].height
                if (i + 1 < placeables.size) {
                    positions += cols.rightStart to y
                    rowHeight = max(rowHeight, placeables[i + 1].height)
                }
                y += rowHeight
                i += 2
            }
            layout(constraints.maxWidth, y) {
                placeables.forEachIndexed { k, p -> p.place(positions[k].first, positions[k].second) }
            }
        }
        }
    }
}, 1f, 1f)
