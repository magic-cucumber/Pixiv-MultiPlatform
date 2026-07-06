import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import top.kagg886.pixko.module.illust.Illust
import top.kagg886.pmf.backend.database.dao.toEntity
import top.kagg886.pmf.backend.database.dao.toIllust

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2026/7/6 21:37
 * ================================================
 */
class GalleryTest {

    @Test
    fun testGalleryBean() {
        val json = Json {
            ignoreUnknownKeys = true
        }
        val multi = json.decodeFromString<Illust>("""
            {
                "id": 125116835,
                "title": "\u521d\u97f3\u30df\u30af",
                "type": "illust",
                "image_urls": {
                  "square_medium": "https:\/\/i.pximg.net\/c\/360x360_70\/img-master\/img\/2024\/12\/12\/14\/14\/24\/125116835_p0_square1200.jpg",
                  "medium": "https:\/\/i.pximg.net\/c\/540x540_70\/img-master\/img\/2024\/12\/12\/14\/14\/24\/125116835_p0_master1200.jpg",
                  "large": "https:\/\/i.pximg.net\/c\/600x1200_90\/img-master\/img\/2024\/12\/12\/14\/14\/24\/125116835_p0_master1200.jpg"
                },
                "caption": "\u5408\u96c6\uff1a\u003Ca href=\u0022https:\/\/www.patreon.com\/BIGxixi\/shop\u0022 target=\u0027_blank\u0027 rel=\u0027noopener noreferrer\u0027\u003Ehttps:\/\/www.patreon.com\/BIGxixi\/shop\u003C\/a\u003E",
                "restrict": 0,
                "user": {
                  "id": 26690900,
                  "name": "BIGxixi",
                  "account": "maou_renjishi",
                  "profile_image_urls": {
                    "medium": "https:\/\/i.pximg.net\/user-profile\/img\/2020\/05\/30\/20\/07\/24\/18736098_647a2527630fea33d07691a864c7de62_170.jpg"
                  },
                  "is_followed": false,
                  "is_accept_request": false
                },
                "tags": [
                  {
                    "name": "\u521d\u97f3\u30df\u30af",
                    "translated_name": "\u521d\u97f3\u672a\u6765"
                  },
                  {
                    "name": "VOCALOID",
                    "translated_name": null
                  },
                  {
                    "name": "miku",
                    "translated_name": null
                  },
                  {
                    "name": "\u767d\u30bf\u30a4\u30c4",
                    "translated_name": "\u767d\u88e4\u889c"
                  },
                  {
                    "name": "\u7f8e\u811a",
                    "translated_name": "\u7f8e\u817f"
                  },
                  {
                    "name": "\u30c4\u30a4\u30f3\u30c6",
                    "translated_name": "\u53cc\u9a6c\u5c3e"
                  },
                  {
                    "name": "\u30ae\u30ea\u30b7\u30e3\u578b",
                    "translated_name": "Greek foot"
                  },
                  {
                    "name": "\u30da\u30c7\u30a3\u30ad\u30e5\u30a2",
                    "translated_name": "\u7f8e\u7532\uff08\u811a\u8dbe\uff09"
                  },
                  {
                    "name": "VOCALOID5000users\u5165\u308a",
                    "translated_name": "VOCALOID 5000\u6536\u85cf"
                  }
                ],
                "tools": [],
                "create_date": "2024-12-12T14:14:24+09:00",
                "page_count": 2,
                "width": 2122,
                "height": 1500,
                "sanity_level": 2,
                "x_restrict": 0,
                "series": null,
                "meta_single_page": {},
                "meta_pages": [
                  {
                    "image_urls": {
                      "square_medium": "https:\/\/i.pximg.net\/c\/360x360_70\/img-master\/img\/2024\/12\/12\/14\/14\/24\/125116835_p0_square1200.jpg",
                      "medium": "https:\/\/i.pximg.net\/c\/540x540_70\/img-master\/img\/2024\/12\/12\/14\/14\/24\/125116835_p0_master1200.jpg",
                      "large": "https:\/\/i.pximg.net\/c\/600x1200_90\/img-master\/img\/2024\/12\/12\/14\/14\/24\/125116835_p0_master1200.jpg",
                      "original": "https:\/\/i.pximg.net\/img-original\/img\/2024\/12\/12\/14\/14\/24\/125116835_p0.jpg"
                    }
                  },
                  {
                    "image_urls": {
                      "square_medium": "https:\/\/i.pximg.net\/c\/360x360_70\/img-master\/img\/2024\/12\/12\/14\/14\/24\/125116835_p1_square1200.jpg",
                      "medium": "https:\/\/i.pximg.net\/c\/540x540_70\/img-master\/img\/2024\/12\/12\/14\/14\/24\/125116835_p1_master1200.jpg",
                      "large": "https:\/\/i.pximg.net\/c\/600x1200_90\/img-master\/img\/2024\/12\/12\/14\/14\/24\/125116835_p1_master1200.jpg",
                      "original": "https:\/\/i.pximg.net\/img-original\/img\/2024\/12\/12\/14\/14\/24\/125116835_p1.jpg"
                    }
                  }
                ],
                "total_view": 47033,
                "total_bookmarks": 6181,
                "is_bookmarked": false,
                "visible": true,
                "is_muted": false,
                "seasonal_effect_animation_urls": null,
                "event_banners": null,
                "total_comments": 21,
                "illust_ai_type": 1,
                "illust_book_style": 0,
                "request": null,
                "restriction_attributes": [
                  "restricted_mode"
                ],
                "comment_access_control": 0
              }

        """.trimIndent())
        val single = json.decodeFromString<Illust>("""
            {
                "id": 146691669,
                "title": "\u30ce\u30be\u30df",
                "type": "illust",
                "image_urls": {
                  "square_medium": "https:\/\/i.pximg.net\/c\/360x360_70\/img-master\/img\/2026\/07\/01\/20\/05\/05\/146691669_p0_square1200.jpg",
                  "medium": "https:\/\/i.pximg.net\/c\/540x540_70\/img-master\/img\/2026\/07\/01\/20\/05\/05\/146691669_p0_master1200.jpg",
                  "large": "https:\/\/i.pximg.net\/c\/600x1200_90\/img-master\/img\/2026\/07\/01\/20\/05\/05\/146691669_p0_master1200.jpg"
                },
                "caption": "",
                "restrict": 0,
                "user": {
                  "id": 120710090,
                  "name": "spade",
                  "account": "user_shjm4774",
                  "profile_image_urls": {
                    "medium": "https:\/\/i.pximg.net\/user-profile\/img\/2026\/01\/15\/22\/25\/10\/28398624_0d40aeaa595f0be6041ddf641075da75_170.jpg"
                  },
                  "is_followed": false,
                  "is_accept_request": false
                },
                "tags": [
                  {
                    "name": "\u30d6\u30eb\u30fc\u30a2\u30fc\u30ab\u30a4\u30d6",
                    "translated_name": "\u78a7\u84dd\u6863\u6848"
                  },
                  {
                    "name": "\u30d6\u30eb\u30a2\u30ab",
                    "translated_name": null
                  },
                  {
                    "name": "\u6a58\u30ce\u30be\u30df",
                    "translated_name": null
                  },
                  {
                    "name": "BlueArchive",
                    "translated_name": null
                  },
                  {
                    "name": "\u30cf\u30a4\u30e9\u30f3\u30c0\u30fc\u9244\u9053\u5b66\u5712",
                    "translated_name": "Highlander Railroad School"
                  },
                  {
                    "name": "\u30b7\u30e5\u30dd\u30ac\u30ad",
                    "translated_name": null
                  }
                ],
                "tools": [],
                "create_date": "2026-07-01T20:05:05+09:00",
                "page_count": 1,
                "width": 1878,
                "height": 1273,
                "sanity_level": 2,
                "x_restrict": 0,
                "series": null,
                "meta_single_page": {
                  "original_image_url": "https:\/\/i.pximg.net\/img-original\/img\/2026\/07\/01\/20\/05\/05\/146691669_p0.png"
                },
                "meta_pages": [],
                "total_view": 1526,
                "total_bookmarks": 434,
                "is_bookmarked": false,
                "visible": true,
                "is_muted": false,
                "seasonal_effect_animation_urls": null,
                "event_banners": null,
                "total_comments": 2,
                "illust_ai_type": 1,
                "illust_book_style": 0,
                "request": null,
                "comment_access_control": 0
              }
        """.trimIndent())

        val single1 = single.toEntity()
        val multi1 = multi.toEntity()

        val single2 = single1.toIllust()
        val multi2 = multi1.toIllust()

        assertEquals(single, single2)
        assertEquals(multi, multi2)
    }
}
