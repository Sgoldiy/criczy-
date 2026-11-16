package app.criczy.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val teams = """{"Teams":[{"Id":1,"Name":"England Men","ShortName":"ENG"}]}"""

    @Test
    fun `previously loaded data is served when the network is gone`() = runTest {
        val server = MockWebServer().apply { start() }
        val monitor = ConnectionMonitor()
        val client = CricketHttp.client(temp.newFolder(), monitor, "test", freshSeconds = 0)
        val source = CricketAuRemoteDataSource.create(client, server.url("/").toString())

        server.enqueue(MockResponse().setBody(teams))
        assertEquals("ENG", source.teams().single().shortName)
        assertFalse(monitor.servingCached.value)

        server.shutdown()
        assertEquals("ENG", source.teams().single().shortName, "served from the offline cache")
        assertTrue(monitor.servingCached.value)

        // Something never loaded has no cache entry, so the failure is reported honestly.
        assertFailsWith<CricketApiException.Network> { source.standings(1) }
    }

    @Test
    fun `identical requests inside the freshness window make one network call`() = runTest {
        val server = MockWebServer().apply { start() }
        val client = CricketHttp.client(temp.newFolder(), ConnectionMonitor(), "test", freshSeconds = 60)
        val source = CricketAuRemoteDataSource.create(client, server.url("/").toString())
        server.enqueue(MockResponse().setBody(teams))

        source.teams()
        source.teams()

        assertEquals(1, server.requestCount)
        assertEquals("test", server.takeRequest().getHeader("User-Agent"))
        server.shutdown()
    }

    @Test
    fun `coming back online clears the cached flag`() = runTest {
        val server = MockWebServer().apply { start() }
        val monitor = ConnectionMonitor()
        val client = CricketHttp.client(temp.newFolder(), monitor, "test", freshSeconds = 0)
        val port = server.port
        val source = CricketAuRemoteDataSource.create(client, server.url("/").toString())
        server.enqueue(MockResponse().setBody(teams))
        source.teams()
        server.shutdown()
        source.teams()
        assertTrue(monitor.servingCached.value)

        val revived = MockWebServer().apply { start(port) }
        revived.enqueue(MockResponse().setBody(teams))
        source.teams()
        assertFalse(monitor.servingCached.value)
        revived.shutdown()
    }
}
