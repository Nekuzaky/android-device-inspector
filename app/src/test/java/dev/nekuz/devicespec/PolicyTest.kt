package dev.nekuz.devicespec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PolicyTest {
    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val manifest = File(main, "AndroidManifest.xml").readText()
    private val sources = File(main, "java").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun manifestDeclaresNoPermissions() {
        assertFalse(manifest.contains("uses-permission"))
    }

    @Test
    fun onlyTheLauncherIsExported() {
        assertEquals(1, Regex("android:exported=\"true\"").findAll(manifest).count())
        assertEquals(2, Regex("android:exported=\"false\"").findAll(manifest).count())
        assertEquals(1, Regex("<intent-filter>").findAll(manifest).count())
    }

    @Test
    fun backupIsOff() {
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        val rules = File(main, "res/xml/data_extraction_rules.xml").readText()
        assertTrue(rules.contains("<cloud-backup>"))
        assertTrue(rules.contains("<device-transfer>"))
        assertFalse(rules.contains("<include"))
    }

    @Test
    fun launcherReadsNothingFromItsIntent() {
        val text = File(main, "java/dev/nekuz/devicespec/MainActivity.kt").readText()
        assertFalse(text.contains("getIntent"))
        assertFalse(text.contains("onNewIntent"))
        assertFalse(Regex("(?<![A-Za-z.(])intent\\b").containsMatchIn(text))
    }

    @Test
    fun sourcesReadNoIdentifiers() {
        val forbidden = listOf(
            "getSerial", "Build.SERIAL", "ANDROID_ID", "getDeviceId", "getImei", "getMeid", "getSubscriberId",
            "TelephonyManager", "getLine1Number", "macAddress", "getHardwareAddress", "NetworkInterface",
            "WifiManager", "AccountManager", "AdvertisingId", "ConnectivityManager", ".uuid", "getUuid",
            "Build.USER", "Build.HOST",
        )
        for (file in sources) {
            val text = file.readText()
            forbidden.forEach { assertFalse("${file.name} uses $it", text.contains(it)) }
        }
    }

    @Test
    fun sourcesDoNotLogOrComment() {
        val forbidden = listOf("android.util.Log", "Log.", "printStackTrace", "println(", "System.out", "System.err", "//", "/*")
        for (file in sources) {
            val text = file.readText()
            forbidden.forEach { assertFalse("${file.name} contains $it", text.contains(it)) }
        }
    }
}
