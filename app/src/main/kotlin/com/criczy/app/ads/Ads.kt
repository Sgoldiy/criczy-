package com.criczy.app.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.nativead.NativeAd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What screens need to know about ads right now. */
data class AdsState(
    /** Consent settled and the SDK started: ads may be requested. */
    val ready: Boolean = false,
    val config: AdsConfig = AdsConfig.Off,
    /** The user watched a rewarded ad: banners and full-screen ads are off until this time. */
    val adFreeUntil: Long = 0,
    /** The consent SDK says Settings must offer "Ad privacy choices" (EEA, UK, US states). */
    val privacyOptions: Boolean = false,
    val rewardedReady: Boolean = false,
) {
    fun shows(unit: AdUnit, now: Long = System.currentTimeMillis()): Boolean {
        if (!ready || !config.usable(unit)) return false
        // The reward removes banners and full-screen ads; native ads, which look like content, stay.
        return unit.format == AdFormat.NATIVE || unit.format == AdFormat.REWARDED || now >= adFreeUntil
    }
}

/** A banner owned by one slot on screen; [loaded] turns true once there is an ad, so empty slots take no space. */
class BannerHandle(val view: AdView, val loaded: StateFlow<Boolean>)

/**
 * Ads for the whole app. [GoogleAds] serves real (or, in debug builds, test) ads; [NoAds] is used by tests and
 * whenever ads are unavailable, and makes every slot draw nothing.
 */
interface AdsBackend {
    val state: StateFlow<AdsState>

    /** Called once the user is past onboarding: settles consent (showing Google's form where required), then starts the SDK. */
    fun start(activity: Activity) {}

    /** The ad for one native slot, kept for that slot while it is fresh. Null when there is none. */
    suspend fun nativeAd(unit: AdUnit, slot: String): NativeAd? = null

    /** A banner for a slot about to be shown, [widthDp] wide. Null when this unit is not showing. */
    fun banner(context: Context, unit: AdUnit, widthDp: Int): BannerHandle? = null

    /** Load an interstitial now, because the user may soon reach a natural break (a finished match). */
    fun prepareInterstitial(activity: Activity) {}

    /** At a natural break: show the interstitial if it is loaded and the caps allow. */
    fun onNaturalBreak(activity: Activity) {}

    fun prepareRewarded(activity: Activity) {}

    /** Shows the rewarded ad; [onReward] runs when the user earned it. */
    fun showRewarded(activity: Activity, onReward: () -> Unit) {}

    fun showPrivacyOptions(activity: Activity) {}

    /** The app was opened from a notification, widget or link: no full-screen ad for a while. */
    fun onOpenedFromLink() {}
}

object NoAds : AdsBackend {
    override val state: StateFlow<AdsState> = MutableStateFlow(AdsState())
}

/** For placement screenshots: every slot draws a labelled placeholder of the real size. */
object PreviewAds : AdsBackend {
    override val state: StateFlow<AdsState> = MutableStateFlow(AdsState(ready = true, config = AdsConfig.Test))
}

val LocalAds = staticCompositionLocalOf<AdsBackend> { NoAds }
