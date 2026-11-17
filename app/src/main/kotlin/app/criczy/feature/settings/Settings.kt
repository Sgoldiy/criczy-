package app.criczy.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.criczy.BuildConfig
import app.criczy.core.data.ThemeMode
import app.criczy.core.data.UserPreferences
import app.criczy.di.AppContainer
import app.criczy.ui.LocalAppContainer
import app.criczy.ui.WhileUiVisible
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = container.preferences.preferences.stateIn(viewModelScope, WhileUiVisible, UserPreferences())

    private fun edit(block: suspend () -> Unit) { viewModelScope.launch { block() } }
    fun setTheme(mode: ThemeMode) = edit { container.preferences.setThemeMode(mode) }
    fun setDynamicColor(on: Boolean) = edit { container.preferences.setDynamicColor(on) }
    fun setAlerts(on: Boolean) = edit { container.preferences.setAlertsEnabled(on) }
    fun setAlertStart(on: Boolean) = edit { container.preferences.setAlertMatchStart(on) }
    fun setAlertWickets(on: Boolean) = edit { container.preferences.setAlertWickets(on) }
    fun setAlertResults(on: Boolean) = edit { container.preferences.setAlertResults(on) }
    fun setConfirmExit(on: Boolean) = edit { container.preferences.setConfirmExit(on) }
}

data class SettingsActions(
    val onTheme: (ThemeMode) -> Unit, val onDynamicColor: (Boolean) -> Unit, val onAlerts: (Boolean) -> Unit,
    val onAlertStart: (Boolean) -> Unit, val onAlertWickets: (Boolean) -> Unit, val onAlertResults: (Boolean) -> Unit,
    val onConfirmExit: (Boolean) -> Unit, val onTeams: () -> Unit, val onPrivacy: () -> Unit,
    val onSystemNotifications: () -> Unit, val onRate: () -> Unit, val onShareApp: () -> Unit,
)

private fun Context.launch(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No app can handle it (for example no store on this device); nothing sensible to do.
    }
}

@Composable
fun SettingsRoute(onTeams: () -> Unit, onPrivacy: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (!granted) vm.setAlerts(false) }
    val storeId = context.packageName.removeSuffix(".debug")
    SettingsScreen(
        prefs,
        SettingsActions(
            onTheme = vm::setTheme, onDynamicColor = vm::setDynamicColor,
            onAlerts = { on ->
                vm.setAlerts(on)
                if (on && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onAlertStart = vm::setAlertStart, onAlertWickets = vm::setAlertWickets, onAlertResults = vm::setAlertResults,
            onConfirmExit = vm::setConfirmExit, onTeams = onTeams, onPrivacy = onPrivacy,
            onSystemNotifications = {
                context.launch(
                    if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                )
            },
            onRate = { context.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$storeId"))) },
            onShareApp = {
                context.launch(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "Live cricket scores, ball-by-ball and match analysis on Criczy: https://play.google.com/store/apps/details?id=$storeId"),
                        "Share Criczy",
                    ),
                )
            },
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: UserPreferences, actions: SettingsActions) {
    Scaffold(topBar = { TopAppBar(title = { Text("MORE", style = MaterialTheme.typography.headlineSmall) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Group("Browse")
            LinkRow("All teams", "Follow teams and see their fixtures", actions.onTeams)

            Group("Appearance")
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (mode, label) ->
                    FilterChip(prefs.themeMode == mode, onClick = { actions.onTheme(mode) }, label = { Text(label) })
                }
            }
            if (Build.VERSION.SDK_INT >= 31) SwitchRow("Use wallpaper colours", "Match the app's colours to your wallpaper", prefs.dynamicColor, actions.onDynamicColor)

            Group("Match alerts")
            SwitchRow("Alerts for favourites", "Checked about every 15 minutes for teams, series and matches you follow", prefs.alertsEnabled, actions.onAlerts)
            SwitchRow("Match starting", "Shortly before the start, and when play begins", prefs.alertMatchStart, actions.onAlertStart, enabled = prefs.alertsEnabled)
            SwitchRow("Results", "When a followed match finishes", prefs.alertResults, actions.onAlertResults, enabled = prefs.alertsEnabled)
            SwitchRow("Wickets", "May arrive a few minutes after the wicket", prefs.alertWickets, actions.onAlertWickets, enabled = prefs.alertsEnabled)
            LinkRow("System notification settings", "Sound, vibration and lock screen", actions.onSystemNotifications)

            Group("General")
            SwitchRow("Confirm before exit", "Ask before closing the app from the home screen", prefs.confirmExit, actions.onConfirmExit)

            Group("About")
            LinkRow("Rate Criczy", "Tell others what you think on Google Play", actions.onRate)
            LinkRow("Share the app", "Send a link to a friend", actions.onShareApp)
            LinkRow("Privacy policy", "What the app stores and sends", actions.onPrivacy)
            Text(
                "Version ${BuildConfig.VERSION_NAME}\nScores, news and images are provided by cricket.com.au. Criczy is not affiliated with Cricket Australia or any cricket board.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun Group(title: String) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 8.dp))
    Text(title.uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

internal val privacySections = listOf(
    "Summary" to "Criczy does not ask you to sign in, does not collect personal information, and contains no advertising or analytics software.",
    "What stays on your device" to "Your favourites, settings, the list of players you have come across, and a cache of scores so the app works offline. These never leave your device, and are removed when you clear the app's storage or uninstall it.",
    "What is sent over the internet" to "To show scores, news and images the app requests them from cricket.com.au's servers. As with any website, those servers receive your IP address and basic technical details of the request. Criczy adds no identifiers to these requests.",
    "Notifications" to "Match alerts are worked out on your device by checking scores periodically. No notification service or token is involved. You can turn alerts off in More, or in your system settings.",
    "Links" to "News stories, videos, tickets and maps open in your browser or other apps, which have their own privacy practices.",
    "Children" to "The app is suitable for general audiences and does not knowingly collect information from anyone.",
    "Changes and contact" to "If this policy changes, the updated version will be published with an app update. Contact details for questions are on the app's Google Play listing.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PRIVACY POLICY", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            privacySections.forEach { (title, body) ->
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
