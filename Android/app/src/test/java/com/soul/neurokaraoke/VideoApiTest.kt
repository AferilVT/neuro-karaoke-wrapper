package com.soul.neurokaraoke

import com.soul.neurokaraoke.data.api.VideoApi
import com.soul.neurokaraoke.data.api.VideoCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoApiTest {
    private val sample = """
      {"items":[
        {"id":"A1","name":"Shrek","description":"d","cloudflareId":"guid-1",
         "thumbnailUrl":"https://vz-26de8a11-dde.b-cdn.net/guid-1/thumbnail.jpg","category":2,
         "views":694,"upvotes":24,
         "songId":null,"songTitle":null,"createdBy":"flashfire8","creatorAvatarUrl":null}
      ],"totalCount":1368,"page":1,"pageSize":1}
    """.trimIndent()

    @Test
    fun parses_page() {
        val page = VideoApi().parseVideoPage(sample)
        assertEquals(1368, page.totalCount)
        assertEquals(1, page.items.size)
        val v = page.items[0]
        assertEquals("Shrek", v.name)
        assertEquals(2, v.category)
        assertEquals(694, v.views)
        assertNull(v.songId)
        assertEquals("flashfire8", v.createdBy)
    }

    @Test
    fun builds_hls_url() {
        val v = VideoApi().parseVideoPage(sample).items[0]
        assertEquals("https://vz-26de8a11-dde.b-cdn.net/guid-1/playlist.m3u8", v.hlsUrl)
    }

    @Test
    fun hls_url_from_thumbnail_when_cloudflareId_null() {
        // Karaoke videos have cloudflareId = null; the guid lives in thumbnailUrl.
        val karaoke = """
          {"items":[{"id":"K1","name":"n","description":"d","cloudflareId":null,
           "thumbnailUrl":"https://vz-26de8a11-dde.b-cdn.net/abc-123/thumbnail.jpg",
           "category":0,"views":1,"upvotes":0,"songId":null,"songTitle":null,
           "createdBy":null,"creatorAvatarUrl":null}],"totalCount":1}
        """.trimIndent()
        val v = VideoApi().parseVideoPage(karaoke).items[0]
        assertNull(v.songId)
        assertEquals("https://vz-26de8a11-dde.b-cdn.net/abc-123/playlist.m3u8", v.hlsUrl)
    }

    @Test
    fun category_labels_and_order() {
        assertEquals(listOf(2, 0), VideoCategories.ORDER)
        assertEquals("Watchalongs", VideoCategories.label(2))
        assertEquals("Karaoke Videos", VideoCategories.label(0))
        assertEquals("Videos", VideoCategories.label(1))
    }
}
