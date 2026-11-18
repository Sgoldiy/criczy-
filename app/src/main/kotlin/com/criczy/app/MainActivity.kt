package com.criczy.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.criczy.app.core.data.UserPreferences
import androidx.compose.runtime.saveable.rememberSaveable
import com.criczy.app.feature.onboarding.OnboardingScreen
import com.criczy.app.feature.onboarding.TeamPickerRoute
import com.criczy.app.navigation.CriczyNavHost
import com.criczy.app.navigation.Routes
import com.criczy.app.ads.LocalAds
import com.criczy.app.ui.LocalAppContainer
import com.criczy.app.ui.theme.CriczyTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CriczyApp).container
        if (savedInstanceState == null && intent?.data?.scheme == "criczy") container.ads.onOpenedFromLink()
        // Hold the splash until preferences are read, so the theme and first screen never flicker.
        // Null until the first read from disk.
        val preferences = container.preferences.preferences.map<UserPreferences, UserPreferences?> { it }
        var ready = false
        splash.setKeepOnScreenCondition { !ready }

        setContent {
            val prefs by preferences.collectAsStateWithLifecycle(initialValue = null)
            val loaded = prefs ?: return@setContent
            ready = true
            val scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalAppContainer provides container, LocalAds provides container.ads) {
                CriczyTheme(loaded.themeMode, loaded.dynamicColor) {
                    if (!loaded.onboardingDone) {
                        // The intro pages, then a team picker; finishing either way ends onboarding.
                        var picking by rememberSaveable { mutableStateOf(false) }
                        val finish: () -> Unit = { scope.launch { container.preferences.setOnboardingDone() } }
                        if (picking) TeamPickerRoute(onDone = finish) else OnboardingScreen(onFinish = { picking = true })
                    } else {
                        val navController = rememberNavController()
                        val entry by navController.currentBackStackEntryAsState()
                        var confirmExit by remember { mutableStateOf(false) }

                        // Ask for notification permission once the user is in the app, not on the splash.
                        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                        LaunchedEffect(Unit) {
                            if (Build.VERSION.SDK_INT >= 33 && loaded.alertsEnabled &&
                                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                                !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) && savedInstanceState == null
                            ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }

                        // Consent and ads start once the user is past onboarding, never over the intro.
                        LaunchedEffect(Unit) { container.ads.start(this@MainActivity) }
                        CriczyNavHost(navController)
                        BackHandler(enabled = loaded.confirmExit && entry?.destination?.route == Routes.HOME) { confirmExit = true }
                        if (confirmExit) {
                            AlertDialog(
                                onDismissRequest = { confirmExit = false },
                                title = { Text("Exit Criczy?") },
                                text = { Text("You'll still get alerts for the matches you follow.") },
                                confirmButton = { TextButton(onClick = { finish() }) { Text("Exit") } },
                                dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Stay") } },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.data?.scheme == "criczy") (application as CriczyApp).container.ads.onOpenedFromLink()
    }
}
