package app.criczy.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Space taken by the floating bottom bar. Top-level screens draw behind the bar, so their
 * scrolling content adds this at the end to keep the last item reachable. It is 0 on detail
 * screens and on tablets, where the bar is not shown.
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/** [base] with the floating bottom bar's height added below it. */
@Composable
fun bottomBarPadding(base: Dp = 0.dp): Dp = base + LocalBottomBarInset.current

/** Padding for a top-level screen's scrolling list: [all] on every side, plus the bar below. */
@Composable
fun listPaddingAboveBar(all: Dp): PaddingValues =
    PaddingValues(start = all, top = all, end = all, bottom = bottomBarPadding(all))
