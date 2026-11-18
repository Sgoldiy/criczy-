package com.criczy.app.ads

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The seven ad units. Each has its own object under `ads_config` in Firebase Realtime Database, keyed by [key];
 * see docs/ADS.md for every field. [testId] is Google's sample unit, used by debug builds whatever the database says.
 */
enum class AdUnit(val key: String, val format: AdFormat, val testId: String) {
    NATIVE_HOME("native_home", AdFormat.NATIVE, "ca-app-pub-3940256099942544/2247696110"),
    NATIVE_NEWS("native_news", AdFormat.NATIVE, "ca-app-pub-3940256099942544/2247696110"),
    BANNER_MATCH("banner_match", AdFormat.BANNER, "ca-app-pub-3940256099942544/9214589741"),
    BANNER_BROWSE("banner_browse", AdFormat.BANNER, "ca-app-pub-3940256099942544/9214589741"),
    INTERSTITIAL("interstitial", AdFormat.INTERSTITIAL, "ca-app-pub-3940256099942544/1033173712"),
    REWARDED("rewarded", AdFormat.REWARDED, "ca-app-pub-3940256099942544/5224354917"),
    APP_OPEN("app_open", AdFormat.APP_OPEN, "ca-app-pub-3940256099942544/9257395921"),
}

enum class AdFormat { NATIVE, BANNER, INTERSTITIAL, REWARDED, APP_OPEN }

/** One unit's settings. Fields a format does not use are ignored. */
data class AdUnitConfig(
    val enabled: Boolean,
    val unitId: String,
    /** Native: list position of the first ad (after this many items) and items between ads. */
    val firstPosition: Int = 3,
    val interval: Int = 8,
    /** Full-screen formats: minutes between two ads of this unit, ads per day, hours after install before the first. */
    val minIntervalMinutes: Int = 4,
    val dailyCap: Int = 3,
    val graceHours: Int = 48,
    /** Full-screen formats: no ad for this long after opening the app from a notification, widget or link. */
    val quietAfterLinkMinutes: Int = 10,
    /** App open: minutes the app must have been in the background. */
    val minAwayMinutes: Int = 120,
    /** Rewarded: hours without banners, interstitials and app open ads after watching. */
    val rewardHours: Int = 24,
) {
    val usable: Boolean get() = enabled && unitId.startsWith("ca-app-pub-")

    companion object {
        fun defaults(unit: AdUnit, unitId: String = "", enabled: Boolean = false) = when (unit) {
            AdUnit.NATIVE_HOME -> AdUnitConfig(enabled, unitId, firstPosition = 3, interval = 8)
            AdUnit.NATIVE_NEWS -> AdUnitConfig(enabled, unitId, firstPosition = 4, interval = 6)
            AdUnit.BANNER_MATCH, AdUnit.BANNER_BROWSE -> AdUnitConfig(enabled, unitId)
            AdUnit.INTERSTITIAL -> AdUnitConfig(enabled, unitId, minIntervalMinutes = 4, dailyCap = 3, graceHours = 48, quietAfterLinkMinutes = 10)
            AdUnit.REWARDED -> AdUnitConfig(enabled, unitId, rewardHours = 24)
            AdUnit.APP_OPEN -> AdUnitConfig(enabled, unitId, dailyCap = 1, graceHours = 168, quietAfterLinkMinutes = 10, minAwayMinutes = 120)
        }
    }
}

/** Everything the database says about ads. [enabled] is the master switch. */
data class AdsConfig(val enabled: Boolean, val units: Map<AdUnit, AdUnitConfig>, val testDeviceIds: List<String> = emptyList()) {
    fun unit(unit: AdUnit): AdUnitConfig = units[unit] ?: AdUnitConfig.defaults(unit)
    fun usable(unit: AdUnit): Boolean = enabled && unit(unit).usable

    companion object {
        /** No ads: until a configuration has been read, and whenever it cannot be. */
        val Off = AdsConfig(false, emptyMap())

        /** Debug builds: every unit on, with Google's test ads. */
        val Test = AdsConfig(true, AdUnit.entries.associateWith { AdUnitConfig.defaults(it, it.testId, enabled = true) })
    }
}

/**
 * Reads the `ads_config` node as Firebase hands it over (maps, Longs, Booleans, Strings). Anything missing or of the
 * wrong type falls back to the defaults; a unit without an id is off. Debug builds keep the database's switches and
 * numbers but always use test unit ids, so nobody ever taps a real ad from a development build.
 */
fun parseAdsConfig(value: Any?, debug: Boolean): AdsConfig {
    val root = value as? Map<*, *> ?: return if (debug) AdsConfig.Test else AdsConfig.Off
    fun Map<*, *>.bool(key: String, default: Boolean) = this[key] as? Boolean ?: default
    fun Map<*, *>.int(key: String, default: Int) = (this[key] as? Number)?.toInt()?.takeIf { it >= 0 } ?: default
    val units = AdUnit.entries.associateWith { unit ->
        val node = root[unit.key] as? Map<*, *>
        val base = AdUnitConfig.defaults(unit)
        if (node == null) {
            base.copy(enabled = debug, unitId = if (debug) unit.testId else "")
        } else {
            AdUnitConfig(
                enabled = node.bool("enabled", false),
                unitId = if (debug) unit.testId else (node["unit_id"] as? String).orEmpty().trim(),
                firstPosition = node.int("first_position", base.firstPosition),
                interval = node.int("interval", base.interval).coerceAtLeast(2),
                minIntervalMinutes = node.int("min_interval_minutes", base.minIntervalMinutes),
                dailyCap = node.int("daily_cap", base.dailyCap),
                graceHours = node.int("grace_hours", base.graceHours),
                quietAfterLinkMinutes = node.int("quiet_after_link_minutes", base.quietAfterLinkMinutes),
                minAwayMinutes = node.int("min_away_minutes", base.minAwayMinutes),
                rewardHours = node.int("reward_hours", base.rewardHours).coerceIn(1, 72),
            )
        }
    }
    val devices = (root["test_device_ids"] as? List<*>)?.filterIsInstance<String>()
        ?: (root["test_device_ids"] as? Map<*, *>)?.values?.filterIsInstance<String>()
        ?: emptyList()
    return AdsConfig(root.bool("enabled", false), units, devices)
}

/** What has been shown, kept on the device so caps survive restarts. Times are epoch milliseconds. */
data class AdsHistory(
    val firstOpen: Long = 0,
    val lastShown: Map<AdUnit, Long> = emptyMap(),
    val shownToday: Map<AdUnit, Int> = emptyMap(),
    val shownDay: Long = 0,
    val adFreeUntil: Long = 0,
    val lastLinkOpen: Long = 0,
    val lastBackground: Long = 0,
)

/** The rules for when ads may appear. Pure, so every cap is unit-tested. */
object AdsPolicy {
    /** List indexes (counting only the list's own items) after which a native ad goes. */
    fun nativePositions(itemCount: Int, config: AdUnitConfig): List<Int> {
        if (itemCount <= config.firstPosition) return emptyList()
        return generateSequence(config.firstPosition) { it + config.interval }.takeWhile { it < itemCount }.toList()
    }

    fun adFree(history: AdsHistory, now: Long): Boolean = now < history.adFreeUntil

    fun epochDay(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()

    /** Interstitial or app open: grace period after install, quiet after a link, spacing, daily cap, ad-free reward. */
    fun mayShowFullScreen(unit: AdUnit, config: AdUnitConfig, history: AdsHistory, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!config.usable || adFree(history, now)) return false
        if (history.firstOpen == 0L || now - history.firstOpen < Duration.ofHours(config.graceHours.toLong()).toMillis()) return false
        if (now - history.lastLinkOpen < Duration.ofMinutes(config.quietAfterLinkMinutes.toLong()).toMillis()) return false
        val last = history.lastShown[unit] ?: 0
        if (now - last < Duration.ofMinutes(config.minIntervalMinutes.toLong()).toMillis()) return false
        val today = if (history.shownDay == epochDay(now, zone)) history.shownToday[unit] ?: 0 else 0
        if (today >= config.dailyCap) return false
        if (unit == AdUnit.APP_OPEN) {
            if (history.lastBackground == 0L || now - history.lastBackground < Duration.ofMinutes(config.minAwayMinutes.toLong()).toMillis()) return false
        }
        return true
    }

    fun recordShown(unit: AdUnit, history: AdsHistory, now: Long, zone: ZoneId = ZoneId.systemDefault()): AdsHistory {
        val day = epochDay(now, zone)
        val counts = if (history.shownDay == day) history.shownToday else emptyMap()
        return history.copy(lastShown = history.lastShown + (unit to now), shownToday = counts + (unit to (counts[unit] ?: 0) + 1), shownDay = day)
    }

    /** Ads keep for an hour (app open: four), after which Google no longer counts them; older ones are thrown away. */
    fun fresh(format: AdFormat, loadedAt: Long, now: Long): Boolean =
        now - loadedAt < if (format == AdFormat.APP_OPEN) Duration.ofHours(4).toMillis() - 60_000 else Duration.ofMinutes(55).toMillis()
}
