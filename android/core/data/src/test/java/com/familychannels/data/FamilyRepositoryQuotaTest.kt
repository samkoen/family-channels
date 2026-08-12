package com.familychannels.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.familychannels.data.api.ChildApi
import com.familychannels.domain.error.QuotaExceededException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class FamilyRepositoryQuotaTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: FamilyRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        server = MockWebServer()
        server.start()
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ChildApi::class.java)
        val context: Context = ApplicationProvider.getApplicationContext()
        val store = SessionStore(context)
        store.saveSession("test-token", "ABCD", "child-1")
        repo = FamilyRepositoryImpl(api, store)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun heartbeatMaps403QuotaExceeded() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"detail":"quota_exceeded"}""")
                .addHeader("Content-Type", "application/json"),
        )
        try {
            repo.heartbeat(1)
            assertTrue("expected QuotaExceededException", false)
        } catch (_: QuotaExceededException) {
            // expected
        }
    }

    @Test
    fun heartbeatReturnsQuotaWhenOk() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "minutes_remaining": 5,
                      "minutes_used": 55,
                      "daily_limit_minutes": 60,
                      "can_watch": true
                    }
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )
        val quota = repo.heartbeat(1)
        assertEquals(5, quota.minutesRemaining)
        assertTrue(quota.canWatch)
        val request = server.takeRequest()
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        assertTrue(request.path!!.endsWith("/api/child/watch/heartbeat"))
    }

    @Test
    fun heartbeatReturnsCanWatchFalseOnLastMinute() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "minutes_remaining": 0,
                      "minutes_used": 60,
                      "daily_limit_minutes": 60,
                      "can_watch": false
                    }
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )
        val quota = repo.heartbeat(1)
        assertFalse(quota.canWatch)
        assertEquals(0, quota.minutesRemaining)
    }
}
