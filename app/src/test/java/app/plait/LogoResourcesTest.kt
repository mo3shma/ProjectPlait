package app.plait

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoResourcesTest {
    private val res = File("src/main/res")

    @Test
    fun launcherAndNotificationIconsExist() {
        val files = listOf(
            "drawable/ic_launcher_background.xml", "drawable/ic_launcher_foreground.xml",
            "drawable/ic_launcher_monochrome.xml", "drawable/ic_notification.xml", "drawable/ic_plait_mark.xml",
            "mipmap-anydpi-v26/ic_launcher.xml", "mipmap-anydpi-v26/ic_launcher_round.xml",
        )
        files.forEach { assertTrue("$it missing", File(res, it).isFile) }
    }
}
