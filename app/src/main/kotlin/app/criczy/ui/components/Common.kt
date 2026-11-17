package app.criczy.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.criczy.core.data.LoadError
import app.criczy.ui.theme.ScoreFont
import app.criczy.ui.theme.cricketColors
import app.criczy.ui.theme.parseTeamColor
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent

/**
 * Team or competition badge. Shows the remote logo when it loads and otherwise a disc in the
 * team's colour with its short name, so cards never have holes while offline.
 */
/**
 * Player headshots are cut-outs with the head in the upper part of the picture, so a plain
 * centre crop shows the chest and cuts the face. Photos are cropped from the top and zoomed
 * by this much, which puts the face in the middle of the circle.
 */
private const val PHOTO_ZOOM = 1.3f

@Composable
fun Badge(
    label: String,
    imageUrl: String?,
    colorHex: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    /** A player headshot rather than a logo: framed on the face, on a soft disc. */
    photo: Boolean = false,
) {
    val base = parseTeamColor(colorHex) ?: MaterialTheme.colorScheme.secondaryContainer
    val onBase = if (base.luminance() > 0.5f) Color(0xFF151A22) else Color.White
    val fallback: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(base), contentAlignment = Alignment.Center) {
            Text(
                text = label.take(3).uppercase(),
                color = onBase,
                fontFamily = ScoreFont,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp,
                maxLines = 1,
            )
        }
    }
    val disc = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainerLow))
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .then(if (photo) Modifier.background(disc) else Modifier)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clearAndSetSemantics { },
    ) {
        if (imageUrl.isNullOrBlank()) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = if (photo) Alignment.TopCenter else Alignment.Center,
                modifier = Modifier.fillMaxSize(),
                loading = { fallback() },
                error = { fallback() },
                // Only the loaded photo is zoomed on the face; the initials stay centred.
                success = {
                    SubcomposeAsyncImageContent(
                        modifier = if (photo) {
                            Modifier.fillMaxSize().graphicsLayer {
                                scaleX = PHOTO_ZOOM
                                scaleY = PHOTO_ZOOM
                                transformOrigin = TransformOrigin(0.5f, 0f)
                            }
                        } else {
                            Modifier.fillMaxSize()
                        },
                    )
                },
            )
        }
    }
}

/** A player's headshot in a circle, framed on the face, with their initials until it loads. */
@Composable
fun PlayerPhoto(name: String, imageUrl: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Badge(name.split(' ').mapNotNull { it.firstOrNull() }.take(2).joinToString(""), imageUrl, null, modifier, size, photo = true)
}

@Composable
fun LivePill(modifier: Modifier = Modifier, text: String = "LIVE") {
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "liveDot",
    )
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(cricketColors.live)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { contentDescription = "Live" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).alpha(pulse).background(cricketColors.onLive, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(text, color = cricketColors.onLive, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceContainerHighest, content: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        modifier = modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 8.dp, vertical = 3.dp),
        color = content,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A card-like container used across the app so surfaces stay consistent. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorState(error: LoadError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when (error) {
        LoadError.OFFLINE -> MessageState(
            Icons.Outlined.CloudOff, "You're offline",
            "This hasn't been saved on your device yet. Connect to the internet and try again.",
            modifier, "Try again", onRetry,
        )
        LoadError.SERVER -> MessageState(
            Icons.Outlined.ErrorOutline, "Scores are unavailable right now",
            "The score service isn't responding. This is usually brief.", modifier, "Try again", onRetry,
        )
        LoadError.FORMAT -> MessageState(
            Icons.Outlined.ErrorOutline, "This couldn't be displayed",
            "The data arrived in a form this version of the app doesn't understand. Updating the app may fix it.",
            modifier, "Try again", onRetry,
        )
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    MessageState(Icons.Outlined.SportsCricket, title, body, modifier)
}

/** Thin strip shown when the data on screen is older than the last successful refresh. */
@Composable
fun StaleBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** Pulsing placeholder block for loading states. */
@Composable
fun Skeleton(modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "skeletonAlpha",
    )
    Box(modifier.alpha(alpha).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHighest))
}

@Composable
fun SkeletonList(count: Int = 5, itemHeight: Dp = 116.dp, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp).semantics { contentDescription = "Loading" }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) { Skeleton(Modifier.fillMaxWidth().height(itemHeight)) }
    }
}

@Composable
fun StatCell(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = valueColor, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
