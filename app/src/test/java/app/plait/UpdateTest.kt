package app.plait

import app.plait.data.update.Updater
import app.plait.data.update.WhatsNew
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    @Test fun versionsCompareAsNumbers() {
        assertTrue(Updater.isNewer("0.20.0", "0.19.3"))
        assertTrue(Updater.isNewer("v0.19.10", "0.19.9"))
        assertTrue(Updater.isNewer("1.0", "0.99.9"))
        assertFalse(Updater.isNewer("0.19.0", "0.19.0"))
        assertFalse(Updater.isNewer("0.18.5", "0.19.0"))
    }

    private val release = """{"tag_name": "v0.20.0", "body": "- **Faster.** It is faster.",
        "assets": [
          {"name": "plait.apk", "browser_download_url": "https://example.org/plait.apk", "size": 85000000},
          {"name": "plait-armv7.apk", "browser_download_url": "https://example.org/plait-armv7.apk", "size": 81000000}
        ]}"""

    @Test fun picksTheApkForThisPhone() {
        val arm64 = Updater.parseRelease(release, listOf("arm64-v8a", "armeabi-v7a", "armeabi"))!!
        assertEquals("0.20.0", arm64.version)
        assertEquals("https://example.org/plait.apk", arm64.url)
        assertEquals(85000000L, arm64.bytes)
        val arm32 = Updater.parseRelease(release, listOf("armeabi-v7a", "armeabi"))!!
        assertEquals("https://example.org/plait-armv7.apk", arm32.url)
    }

    @Test fun anOlderReleaseWithOneApkStillWorks() {
        val one = """{"tag_name": "v0.18.4", "assets": [{"name": "plait.apk", "browser_download_url": "u", "size": 1}]}"""
        assertEquals("u", Updater.parseRelease(one, listOf("armeabi-v7a"))!!.url)
        assertNull(Updater.parseRelease("""{"tag_name": "v1", "assets": []}""", listOf("arm64-v8a")))
    }

    @Test fun readsThisVersionFromTheChangelog() {
        val md = """# Changelog

## 0.20.0: updates in the app

- **Updates in the app.** Plait checks GitHub and installs. More words here.
- **A smaller download.** 85 MB instead of 130 MB.
- Settings says "1 calendar".

## 0.19.0: older

- **Old.** Old thing.
"""
        val news = WhatsNew.section(md, "0.20.0")!!
        assertEquals("updates in the app", news.title)
        assertEquals(3, news.items.size)
        assertEquals("Updates in the app.", news.items[0].lead)
        assertEquals("Plait checks GitHub and installs.", news.items[0].detail)
        assertEquals("Settings says \"1 calendar\".", news.items[2].lead)
        assertNull(WhatsNew.section(md, "0.21.0"))
    }
}
