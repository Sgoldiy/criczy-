package com.criczy.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Real ads. Show rate is kept high by requesting an ad only when it is about to be seen: banners and native ads load
 * as their slot comes on screen (with one native ad prepared ahead per unit), the interstitial loads when a finished
 * match opens, the rewarded ad when More opens, and the app open ad when the app goes to the background. Nothing is
 * shown that is not loaded, and nothing waits on the network on the main thread.
 */
class GoogleAds(private val context: Context, private val debug: Boolean) : AdsBackend {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.getSharedPreferences("criczy_ads", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(AdsState(config = cachedConfig(), adFreeUntil = prefs.getLong("ad_free_until", 0)))
    override val state: StateFlow<AdsState> = _state

    private var history = readHistory()
    private val started = AtomicBoolean(false)
    private val initialised = AtomicBoolean(false)
    private var activity = WeakReference<Activity>(null)

    private class Loaded<T>(val ad: T, val at: Long = System.currentTimeMillis())

    private val nativeBySlot = HashMap<String, Loaded<NativeAd>>()
    private val nativeAhead = HashMap<AdUnit, Loaded<NativeAd>>()
    private val nativeLoading = HashSet<AdUnit>()
    private var interstitial: Loaded<InterstitialAd>? = null
    private var interstitialLoading = false
    private var rewarded: Loaded<RewardedAd>? = null
    private var rewardedLoading = false
    private var appOpen: Loaded<AppOpenAd>? = null
    private var appOpenLoading = false
    private var showingFullScreen = false

    init {
        if (history.firstOpen == 0L) saveHistory(history.copy(firstOpen = now()))
        listenForConfig()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = onForeground()
            override fun onStop(owner: LifecycleOwner) = onBackground()
        })
    }

    private fun now() = System.currentTimeMillis()
    private fun unitId(unit: AdUnit) = _state.value.config.unit(unit).unitId
    private fun request() = AdRequest.Builder().build()

    // ---------------------------------------------------------------- configuration from Realtime Database

    /** The last configuration read, so ads can start at once on the next launch without waiting for the network. */
    private fun cachedConfig(): AdsConfig {
        val json = prefs.getString("config", null) ?: return if (debug) AdsConfig.Test else AdsConfig.Off
        return runCatching { parseAdsConfig(toPlain(JSONObject(json)), debug) }.getOrElse { if (debug) AdsConfig.Test else AdsConfig.Off }
    }

    private fun listenForConfig() {
        val database = try {
            FirebaseDatabase.getInstance()
        } catch (e: Exception) {
            Log.i(TAG, "No Firebase Realtime Database (${e.message}); using the cached ad configuration")
            return
        }
        val ref = database.getReference("ads_config")
        ref.keepSynced(true)
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val value = snapshot.value
                if (value is Map<*, *>) prefs.edit().putString("config", JSONObject(value).toString()).apply()
                _state.update { it.copy(config = parseAdsConfig(value, debug)) }
                applyRequestConfiguration()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Ad configuration not readable: ${error.message}")
            }
        })
    }

    // ---------------------------------------------------------------- consent and start-up

    override fun start(activity: Activity) {
        this.activity = WeakReference(activity)
        if (!started.compareAndSet(false, true)) return
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build()
        consent.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                if (error != null) Log.w(TAG, "Consent form: ${error.message}")
                afterConsent(consent)
            }
        }, { error ->
            Log.w(TAG, "Consent update: ${error.message}")
            afterConsent(consent)
        })
        // Consent given in an earlier session: no need to wait for the update before starting.
        if (consent.canRequestAds()) afterConsent(consent)
    }

    private fun afterConsent(consent: ConsentInformation) {
        _state.update { it.copy(privacyOptions = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) }
        if (!consent.canRequestAds() || !initialised.compareAndSet(false, true)) return
        scope.launch {
            applyRequestConfiguration()
            // Initialising on a background thread keeps the first frames smooth.
            withContext(Dispatchers.IO) { MobileAds.initialize(context) {} }
            _state.update { it.copy(ready = true) }
            prepareNative(AdUnit.NATIVE_HOME)
        }
    }

    private fun applyRequestConfiguration() {
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_T)
                .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
                .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE)
                .setTestDeviceIds(_state.value.config.testDeviceIds)
                .build(),
        )
    }

    override fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error -> if (error != null) Log.w(TAG, "Privacy options: ${error.message}") }
    }

    override fun onOpenedFromLink() = saveHistory(history.copy(lastLinkOpen = now()))

    // ---------------------------------------------------------------- native

    override suspend fun nativeAd(unit: AdUnit, slot: String): NativeAd? {
        if (!_state.value.shows(unit)) return null
        nativeBySlot[slot]?.let { if (AdsPolicy.fresh(AdFormat.NATIVE, it.at, now())) return it.ad else it.ad.destroy() }
        val ahead = nativeAhead.remove(unit)?.takeIf { AdsPolicy.fresh(AdFormat.NATIVE, it.at, now()) }
        val loaded = ahead ?: loadNative(unit) ?: return null
        nativeBySlot[slot] = loaded
        prepareNative(unit)
        return loaded.ad
    }

    /** Keeps one native ad ready per unit, so the next slot fills without waiting. */
    private fun prepareNative(unit: AdUnit) {
        if (!_state.value.shows(unit) || nativeAhead.containsKey(unit) || !nativeLoading.add(unit)) return
        scope.launch {
            loadNative(unit)?.let { nativeAhead[unit] = it }
            nativeLoading.remove(unit)
        }
    }

    private suspend fun loadNative(unit: AdUnit): Loaded<NativeAd>? = suspendCancellableCoroutine { cont ->
        AdLoader.Builder(context, unitId(unit))
            .forNativeAd { ad -> if (cont.isActive) cont.resume(Loaded(ad)) else ad.destroy() }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.i(TAG, "${unit.key}: no ad (${error.code} ${error.message})")
                    if (cont.isActive) cont.resume(null)
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build(),
            )
            .build()
            .loadAd(request())
    }

    // ---------------------------------------------------------------- banner

    override fun banner(context: Context, unit: AdUnit, widthDp: Int): BannerHandle? {
        if (!_state.value.shows(unit)) return null
        val loaded = MutableStateFlow(false)
        val view = AdView(context).apply {
            adUnitId = unitId(unit)
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
            adListener = object : AdListener() {
                override fun onAdLoaded() { loaded.value = true }
                override fun onAdFailedToLoad(error: LoadAdError) { Log.i(TAG, "${unit.key}: no ad (${error.code})") }
            }
        }
        view.loadAd(request())
        return BannerHandle(view, loaded)
    }

    // ---------------------------------------------------------------- interstitial

    override fun prepareInterstitial(activity: Activity) {
        val config = _state.value.config.unit(AdUnit.INTERSTITIAL)
        // Only load when it could be shown soon: otherwise it would expire unshown and lower the show rate.
        if (!_state.value.shows(AdUnit.INTERSTITIAL) || interstitialLoading) return
        if (!AdsPolicy.mayShowFullScreen(AdUnit.INTERSTITIAL, config, history, now() + 5 * 60_000)) return
        if (interstitial?.let { AdsPolicy.fresh(AdFormat.INTERSTITIAL, it.at, now()) } == true) return
        interstitialLoading = true
        InterstitialAd.load(activity, unitId(AdUnit.INTERSTITIAL), request(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { interstitial = Loaded(ad); interstitialLoading = false }
            override fun onAdFailedToLoad(error: LoadAdError) { interstitialLoading = false }
        })
    }

    override fun onNaturalBreak(activity: Activity) {
        val loaded = interstitial ?: return
        val config = _state.value.config.unit(AdUnit.INTERSTITIAL)
        if (showingFullScreen || !_state.value.shows(AdUnit.INTERSTITIAL)) return
        if (!AdsPolicy.fresh(AdFormat.INTERSTITIAL, loaded.at, now())) { interstitial = null; return }
        if (!AdsPolicy.mayShowFullScreen(AdUnit.INTERSTITIAL, config, history, now())) return
        interstitial = null
        loaded.ad.fullScreenContentCallback = fullScreenCallback(AdUnit.INTERSTITIAL)
        showingFullScreen = true
        loaded.ad.show(activity)
    }

    private fun fullScreenCallback(unit: AdUnit) = object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() = saveHistory(AdsPolicy.recordShown(unit, history, now()))
        override fun onAdDismissedFullScreenContent() { showingFullScreen = false }
        override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) { showingFullScreen = false }
    }

    // ---------------------------------------------------------------- rewarded

    override fun prepareRewarded(activity: Activity) {
        if (!_state.value.shows(AdUnit.REWARDED) || rewardedLoading) return
        if (rewarded?.let { AdsPolicy.fresh(AdFormat.REWARDED, it.at, now()) } == true) return
        rewardedLoading = true
        RewardedAd.load(activity, unitId(AdUnit.REWARDED), request(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewarded = Loaded(ad); rewardedLoading = false; _state.update { it.copy(rewardedReady = true) } }
            override fun onAdFailedToLoad(error: LoadAdError) { rewardedLoading = false }
        })
    }

    override fun showRewarded(activity: Activity, onReward: () -> Unit) {
        val loaded = rewarded ?: return
        rewarded = null
        _state.update { it.copy(rewardedReady = false) }
        showingFullScreen = true
        loaded.ad.fullScreenContentCallback = fullScreenCallback(AdUnit.REWARDED)
        loaded.ad.show(activity) {
            val until = now() + _state.value.config.unit(AdUnit.REWARDED).rewardHours * 3_600_000L
            prefs.edit().putLong("ad_free_until", until).apply()
            history = history.copy(adFreeUntil = until)
            _state.update { it.copy(adFreeUntil = until) }
            onReward()
        }
    }

    // ---------------------------------------------------------------- app open

    private fun onBackground() {
        saveHistory(history.copy(lastBackground = now()))
        val config = _state.value.config.unit(AdUnit.APP_OPEN)
        val activity = activity.get() ?: return
        // Load now so it is ready when the user comes back, if a return after the away time could show it.
        if (!_state.value.shows(AdUnit.APP_OPEN) || appOpenLoading || showingFullScreen) return
        if (appOpen?.let { AdsPolicy.fresh(AdFormat.APP_OPEN, it.at, now()) } == true) return
        val later = now() + config.minAwayMinutes * 60_000L
        if (!AdsPolicy.mayShowFullScreen(AdUnit.APP_OPEN, config, history.copy(lastBackground = now()), later)) return
        appOpenLoading = true
        AppOpenAd.load(activity, unitId(AdUnit.APP_OPEN), request(), object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) { appOpen = Loaded(ad); appOpenLoading = false }
            override fun onAdFailedToLoad(error: LoadAdError) { appOpenLoading = false }
        })
    }

    private fun onForeground() {
        val loaded = appOpen ?: return
        val activity = activity.get() ?: return
        val config = _state.value.config.unit(AdUnit.APP_OPEN)
        if (showingFullScreen || !_state.value.shows(AdUnit.APP_OPEN)) return
        if (!AdsPolicy.fresh(AdFormat.APP_OPEN, loaded.at, now())) { appOpen = null; return }
        if (!AdsPolicy.mayShowFullScreen(AdUnit.APP_OPEN, config, history, now())) return
        appOpen = null
        showingFullScreen = true
        loaded.ad.fullScreenContentCallback = fullScreenCallback(AdUnit.APP_OPEN)
        loaded.ad.show(activity)
    }

    // ---------------------------------------------------------------- history on disk

    private fun readHistory(): AdsHistory {
        fun map(key: String): Map<String, Long> = runCatching {
            val o = JSONObject(prefs.getString(key, "{}")!!)
            o.keys().asSequence().associateWith { o.getLong(it) }
        }.getOrDefault(emptyMap())
        fun units(m: Map<String, Long>) = m.mapNotNull { (k, v) -> AdUnit.entries.firstOrNull { it.key == k }?.let { it to v } }.toMap()
        return AdsHistory(
            firstOpen = prefs.getLong("first_open", 0),
            lastShown = units(map("last_shown")),
            shownToday = units(map("shown_today")).mapValues { it.value.toInt() },
            shownDay = prefs.getLong("shown_day", 0),
            adFreeUntil = prefs.getLong("ad_free_until", 0),
            lastLinkOpen = prefs.getLong("last_link_open", 0),
            lastBackground = prefs.getLong("last_background", 0),
        )
    }

    private fun saveHistory(next: AdsHistory) {
        history = next
        prefs.edit()
            .putLong("first_open", next.firstOpen)
            .putString("last_shown", JSONObject(next.lastShown.mapKeys { it.key.key }).toString())
            .putString("shown_today", JSONObject(next.shownToday.mapKeys { it.key.key }).toString())
            .putLong("shown_day", next.shownDay)
            .putLong("last_link_open", next.lastLinkOpen)
            .putLong("last_background", next.lastBackground)
            .apply()
    }

    private companion object {
        const val TAG = "CriczyAds"

        /** JSONObject back to the plain maps and lists Firebase hands over. */
        fun toPlain(value: Any?): Any? = when (value) {
            is JSONObject -> value.keys().asSequence().associateWith { toPlain(value.get(it)) }
            is JSONArray -> (0 until value.length()).map { toPlain(value.get(it)) }
            JSONObject.NULL -> null
            else -> value
        }
    }
}
