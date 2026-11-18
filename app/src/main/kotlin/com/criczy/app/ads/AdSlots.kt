package com.criczy.app.ads

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.criczy.app.R
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * A native ad in a list, as a card like the app's own. Draws nothing until an ad has loaded, so a slot with no ad
 * never leaves a gap. [slot] keeps the same ad in the same place while it is fresh.
 */
@Composable
fun NativeAdSlot(unit: AdUnit, slot: String, modifier: Modifier = Modifier) {
    val ads = LocalAds.current
    val state by ads.state.collectAsState()
    if (!state.shows(unit)) return
    if (ads === PreviewAds) {
        AdPlaceholder("Native ad · ${unit.key}", 300, modifier)
        return
    }
    val ad by produceState<NativeAd?>(null, unit, slot) { value = ads.nativeAd(unit, slot) }
    val loaded = ad ?: return
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer) {
        AndroidView(
            factory = { context -> LayoutInflater.from(context).inflate(R.layout.ad_native_card, null) as NativeAdView },
            update = { view ->
                val text = colors.onSurface.toArgb()
                val muted = colors.onSurfaceVariant.toArgb()
                bindNative(view, loaded, text, muted, colors.primary.toArgb(), colors.onPrimary.toArgb())
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun bindNative(view: NativeAdView, ad: NativeAd, text: Int, muted: Int, accent: Int, onAccent: Int) {
    val headline = view.findViewById<TextView>(R.id.ad_headline)
    val body = view.findViewById<TextView>(R.id.ad_body)
    val advertiser = view.findViewById<TextView>(R.id.ad_advertiser)
    val icon = view.findViewById<ImageView>(R.id.ad_icon)
    val cta = view.findViewById<Button>(R.id.ad_cta)
    val badge = view.findViewById<TextView>(R.id.ad_badge)
    val media = view.findViewById<MediaView>(R.id.ad_media)

    headline.text = ad.headline
    headline.setTextColor(text)
    body.text = ad.body
    body.setTextColor(muted)
    body.visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
    advertiser.text = ad.advertiser ?: ad.store ?: ""
    advertiser.setTextColor(muted)
    icon.setImageDrawable(ad.icon?.drawable)
    icon.visibility = if (ad.icon == null) View.GONE else View.VISIBLE
    badge.setTextColor(onAccent)
    badge.background = GradientDrawable().apply { cornerRadius = 8f * view.resources.displayMetrics.density; setColor(accent) }
    cta.text = ad.callToAction
    cta.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    cta.setTextColor(onAccent)
    cta.backgroundTintList = ColorStateList.valueOf(accent)
    media.mediaContent = ad.mediaContent
    media.visibility = if (ad.mediaContent == null) View.GONE else View.VISIBLE

    view.headlineView = headline
    view.bodyView = body
    view.advertiserView = advertiser
    view.iconView = icon
    view.callToActionView = cta
    view.mediaView = media
    view.setNativeAd(ad)
}

/**
 * A banner at the end of a screen's content. The view is created when the slot comes on screen, so the request
 * matches what is seen, and it takes space only once an ad has loaded.
 */
@Composable
fun BannerAdSlot(unit: AdUnit, modifier: Modifier = Modifier) {
    val ads = LocalAds.current
    val state by ads.state.collectAsState()
    if (!state.shows(unit)) return
    if (ads === PreviewAds) {
        AdPlaceholder("Banner ad · ${unit.key}", 60, modifier)
        return
    }
    val context = LocalContext.current
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = maxWidth.value.toInt()
        val handle = remember(unit, width) { ads.banner(context, unit, width) } ?: return@BoxWithConstraints
        DisposableEffect(handle) { onDispose { handle.view.destroy() } }
        val loaded by handle.loaded.collectAsState()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (loaded) {
                Text("Ad", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }
            // Laid out even before it loads (the SDK needs it attached), but with no height until then.
            AndroidView(factory = { handle.view }, modifier = if (loaded) Modifier else Modifier.height(0.dp))
        }
    }
}

/** What placement screenshots show in place of an ad: the slot's real size and the unit's name. */
@Composable
private fun AdPlaceholder(label: String, heightDp: Int, modifier: Modifier) {
    Box(
        modifier.fillMaxWidth().height(heightDp.dp)
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}
