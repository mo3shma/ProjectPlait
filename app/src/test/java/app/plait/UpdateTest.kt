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

    @Test fun parsesGithubDigests() {
        val hex = "ab".repeat(32)
        assertEquals(hex, Updater.parseDigest("sha256:$hex"))
        assertEquals(hex, Updater.parseDigest("SHA256:${hex.uppercase()}"))
        assertNull(Updater.parseDigest(null))
        assertNull(Updater.parseDigest("null"))
        assertNull(Updater.parseDigest("sha256:abc"))
        assertNull(Updater.parseDigest("sha512:" + "ab".repeat(64)))
        val withDigest = """{"tag_name": "v1.0", "assets": [{"name": "plait.apk", "browser_download_url": "u", "size": 1, "digest": "sha256:$hex"}]}"""
        assertEquals(hex, Updater.parseRelease(withDigest, listOf("arm64-v8a"))!!.sha256)
        assertNull(Updater.parseRelease(release, listOf("arm64-v8a"))!!.sha256)
    }

    @Test fun hashesAFile() {
        val f = java.io.File.createTempFile("sha", ".bin").apply { writeText("abc"); deleteOnExit() }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Updater.sha256Hex(f))
    }

    @Test fun abiSelectionPrefersArm64AnywhereInTheList() {
        assertEquals("https://example.org/plait.apk", Updater.parseRelease(release, listOf("x86_64", "arm64-v8a"))!!.url)
        assertEquals("https://example.org/plait-armv7.apk", Updater.parseRelease(release, listOf("armeabi-v7a"))!!.url)
    }

    @Test fun pillMessageShortensOnNarrowScreens() {
        assertEquals("Update 0.28.2 ready", app.plait.ui.screens.pillText("ready", "0.28.2", false))
        assertEquals("0.28.2 ready", app.plait.ui.screens.pillText("ready", "0.28.2", true))
    }

    @Test fun suffixesAndShortVersionsCompare() {
        assertTrue(Updater.isNewer("0.28.1", "0.28.0"))
        assertFalse(Updater.isNewer("0.28", "0.28.0"))
        assertFalse(Updater.isNewer("0.28.0", "0.28.0"))
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
