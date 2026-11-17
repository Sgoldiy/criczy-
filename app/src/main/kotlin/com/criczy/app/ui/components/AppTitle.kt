package com.criczy.app.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.criczy.app.ui.theme.ScoreFont
import com.criczy.app.ui.theme.cricketColors

/**
 * Where a title turns from the plain colour to the accent, the way the CRIC·ZY wordmark does.
 * A fixture ("THU v STA") turns at the second side, a phrase at its last word, and a single
 * word at its last third: CRICZY splits as CRIC + ZY.
 */
internal fun titleSplit(text: String): Triple<String, String, String> {
    val title = text.trim()
    val versus = title.indexOf(" v ")
    if (versus > 0) return Triple(title.substring(0, versus), " v ", title.substring(versus + 3))
    val space = title.lastIndexOf(' ')
    if (space > 0) return Triple(title.substring(0, space), " ", title.substring(space + 1))
    val accent = (title.length / 3).coerceAtLeast(1).coerceAtMost(title.length - 1)
    return if (title.length < 2) Triple(title, "", "") else Triple(title.dropLast(accent), "", title.takeLast(accent))
}

/** The title every top bar uses: condensed bold capitals, ending in the accent colour. */
@Composable
fun AppTitle(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 28.sp) {
    val plain = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = cricketColors.accent
    val styled: AnnotatedString = remember(text, plain, muted, accent) {
        val (start, joint, end) = titleSplit(text)
        buildAnnotatedString {
            withStyle(SpanStyle(color = plain)) { append(start.uppercase()) }
            // The "v" between two sides stays lower case and quiet, as on a scorecard.
            withStyle(SpanStyle(color = if (joint == " v ") muted else plain)) { append(joint) }
            withStyle(SpanStyle(color = accent)) { append(end.uppercase()) }
        }
    }
    Text(
        styled, modifier.scrollingText(), fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = fontSize, letterSpacing = 1.sp,
        maxLines = 1, color = Color.Unspecified,
    )
}

/**
 * Lets one line of text that is too long scroll sideways, like a ticker, so a long name such as
 * "SOUTH AFRICA v NEW ZEALAND" can be read in full. Text that fits stays still.
 */
fun Modifier.scrollingText(): Modifier = basicMarquee(
    iterations = Int.MAX_VALUE,
    initialDelayMillis = 1_500,
    repeatDelayMillis = 2_000,
)
