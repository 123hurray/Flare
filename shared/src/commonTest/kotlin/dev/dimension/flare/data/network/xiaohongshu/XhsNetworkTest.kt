package dev.dimension.flare.data.network.xiaohongshu

import dev.dimension.flare.common.encodeJsonWithDefaults
import dev.dimension.flare.data.network.xiaohongshu.model.XhsFeedRequest
import dev.dimension.flare.data.network.xiaohongshu.model.XhsHomeFeedRequest
import dev.dimension.flare.data.network.xiaohongshu.model.XhsTelemetryEnvelope
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class XhsNetworkTest {
    @BeforeTest
    fun resetRuntimeSigner() {
        XhsSigningRuntime.resetForTests()
    }

    @Test
    fun cookieHeaderKeepsKnownOrderAndDropsBlankValues() {
        val header =
            XhsCookieHeaderBuilder.build(
                mapOf(
                    "web_session" to "session",
                    "unused" to "",
                    "a1" to "a1-value",
                    "webId" to "web-id",
                    "gid" to "gid-value",
                    "custom" to "custom-value",
                ),
            )

        assertEquals(
            "a1=a1-value; webId=web-id; web_session=session; gid=gid-value; custom=custom-value",
            header,
        )
    }

    @Test
    fun baseHeadersIncludeWebCorsShape() {
        val headers = XhsAuthPlugin.baseHeaders("a1=a1-value; web_session=session")

        assertEquals("application/json;charset=UTF-8", headers["Content-Type"])
        assertEquals("https://www.xiaohongshu.com", headers["Origin"])
        assertEquals("https://www.xiaohongshu.com/", headers["Referer"])
        assertEquals("?0", headers["sec-ch-ua-mobile"])
        assertEquals("empty", headers["sec-fetch-dest"])
        assertEquals("cors", headers["sec-fetch-mode"])
        assertEquals("same-site", headers["sec-fetch-site"])
        assertTrue(headers["Cookie"].orEmpty().contains("web_session=session"))
    }

    @Test
    fun signingRequiresA1Cookie() {
        assertFailsWith<IllegalArgumentException> {
            runTest {
                XhsSigning.sign("POST", "/api/sns/web/v1/homefeed", "{}", emptyMap())
            }
        }
    }

    @Test
    fun mainApiSigningIsFailClosedUntilVerified() {
        assertFalse(XhsSigning.IS_MAIN_API_SIGNING_VERIFIED)
    }

    @Test
    fun signingChangesWhenBodyChanges() =
        runTest {
            XhsSigningRuntime.install(
                XhsRuntimeSigner { request ->
                    mapOf(
                        "x-s" to "XYS_${request.body.hashCode()}",
                        "x-s-common" to "common-${request.path}",
                        "x-t" to "1779473270965",
                        "x-b3-traceid" to "1234567890abcdef",
                        "x-xray-traceid" to "1234567890abcdef1234567890abcdef",
                    )
                },
            )
            val cookies = mapOf("a1" to "a1-value")
            val first = XhsSigning.sign("POST", "/api/sns/web/v1/homefeed", """{"num":20}""", cookies)
            val second = XhsSigning.sign("POST", "/api/sns/web/v1/homefeed", """{"num":40}""", cookies)

            assertTrue(first.keys.containsAll(listOf("x-s", "x-s-common", "x-t", "x-b3-traceid", "x-xray-traceid")))
            assertTrue(first["x-s"].orEmpty().startsWith("XYS_"))
            assertFalse(first["x-s"].orEmpty().startsWith("XYW_"))
            assertTrue(first["x-s-common"].orEmpty().isNotBlank())
            assertEquals(16, first["x-b3-traceid"]?.length)
            assertEquals(32, first["x-xray-traceid"]?.length)
            assertNotEquals(first["x-s"], second["x-s"])
        }

    @Test
    fun telemetryIsModeledButDisabledByDefault() =
        runTest {
            val api = XhsTelemetryApi()
            assertTrue(api.supportedHeaders.contains("X-Sign"))
            assertTrue(api.supportedHeaders.contains("X-Mx-ReqToken"))
            assertFalse(api.collect(XhsTelemetryEnvelope()))
            assertFalse(api.apm(XhsTelemetryEnvelope()))
        }

    @Test
    fun homeFeedBodyKeepsWebDefaultFields() {
        val body = XhsHomeFeedRequest().encodeJsonWithDefaults()

        assertTrue(body.contains(""""cursor_score":""""))
        assertTrue(body.contains(""""num":39"""))
        assertTrue(body.contains(""""refresh_type":1"""))
        assertTrue(body.contains(""""note_index":0"""))
        assertTrue(body.contains(""""unread_begin_note_id":""""))
        assertTrue(body.contains(""""unread_end_note_id":""""))
        assertTrue(body.contains(""""unread_note_count":0"""))
        assertTrue(body.contains(""""category":"homefeed_recommend""""))
        assertTrue(body.contains(""""search_key":""""))
        assertTrue(body.contains(""""need_num":40"""))
        assertTrue(body.contains(""""image_scenes":["FD_PRV_WEBP","FD_WM_WEBP"]"""))
        assertFalse(body.contains(""""image_formats""""))
        assertFalse(body.contains(""""need_filter_image""""))
    }

    @Test
    fun feedBodyKeepsWebDefaultFields() {
        val body =
            XhsFeedRequest(
                sourceNoteId = "note-id",
                xsecSource = "pc_feed",
                xsecToken = "token",
            ).encodeJsonWithDefaults()

        assertTrue(body.contains(""""source_note_id":"note-id""""))
        assertTrue(body.contains(""""image_formats":["jpg","webp","avif"]"""))
        assertTrue(body.contains(""""extra":{"need_body_topic":"1"}"""))
        assertTrue(body.contains(""""xsec_source":"pc_feed""""))
        assertTrue(body.contains(""""xsec_token":"token""""))
    }
}
