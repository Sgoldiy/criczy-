package com.criczy.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import com.criczy.app.core.data.CriczyDatabase
import com.criczy.app.core.data.ThemeMode
import com.criczy.app.core.network.ConnectionMonitor
import com.criczy.app.core.network.CricketAuRemoteDataSource
import com.criczy.app.core.network.CricketHttp
import com.criczy.app.core.network.CricketRemoteDataSource
import com.criczy.app.di.AppContainer
import com.criczy.app.ui.LocalAppContainer
import com.criczy.app.ui.theme.CriczyTheme
import com.github.takahirom.roborazzi.captureRoboImage
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/** Serves the responses captured from the live API, so tests exercise the real client and mappers. */
object FixtureServer {
    var offline = false

    /** Applied to every fixture body; the store screenshots use it to swap in other teams and players. */
    var rewrite: (String) -> String = { it }

    private fun fixture(name: String): MockResponse =
        MockResponse().setBody(rewrite(checkNotNull(javaClass.getResource("/fixtures/$name")) { name }.readText()))

    val server: MockWebServer by lazy {
        MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (offline) return MockResponse().setResponseCode(503)
                    val url = request.requestUrl!!
                    val path = url.encodedPath
                    return when {
                        path.endsWith("mobile/views/fixtures") -> fixture("fixtures.json")
                        path.endsWith("views/scorecard") ->
                            if (url.queryParameter("FixtureId") == "41015") fixture("summary_test_match.json") else fixture("scorecard.json")
                        path.endsWith("mobile/views/comments") -> fixture("comments_mobile.json")
                        path.endsWith("web/views/comments") ->
                            if (url.queryParameter("InningNumber") == "1") fixture("comments_web_innings.json") else MockResponse().setBody("""{"Inning":{"InningNumber":2,"Overs":[]}}""")
                        path.endsWith("views/standings") -> fixture("standings.json")
                        path.endsWith("web/competitions") -> fixture("competitions.json")
                        path.endsWith("web/teams") -> fixture("teams.json")
                        path.endsWith("web/players") -> fixture("players.json")
                        path.endsWith("mobile/news") -> fixture("news.json")
                        path.endsWith("views/home") -> fixture("home.json")
                        path.endsWith("views/highlights") -> fixture("highlights.json")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start()
        }
    }

    fun remote(): CricketRemoteDataSource =
        CricketAuRemoteDataSource.create(CricketHttp.client(null, ConnectionMonitor(), "test"), server.url("/").toString())
}

/** Application used under Robolectric: fixture-backed data, in-memory database, no background work. */
class TestCriczyApp : CriczyApp() {
    override fun createContainer(): AppContainer =
        AppContainer(
            this, remoteOverride = FixtureServer.remote(), databaseOverride = CriczyDatabase.inMemory(this),
            communityOverride = com.criczy.app.core.data.UnavailableCommunityBackend,
        )

    override fun startBackgroundWork() = Unit
}

fun ComposeContentTestRule.setCriczyContent(dark: Boolean = true, content: @Composable () -> Unit) {
    val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<TestCriczyApp>()
    setContent {
        CompositionLocalProvider(LocalAppContainer provides app.container) {
            CriczyTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { content() }
        }
    }
}

fun ComposeContentTestRule.awaitText(text: String, substring: Boolean = true, timeoutMs: Long = 10_000) {
    waitUntil(timeoutMs) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
}

fun ComposeContentTestRule.screenshot(name: String) {
    waitForIdle()
    onRoot().captureRoboImage("build/screenshots/$name.png")
}
