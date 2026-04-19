package dev.perf.cli.sdk

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AndroidSdkLocatorTest {

    @Test
    fun `hasAdb false for empty directory`(@TempDir dir: File) {
        assertTrue(!AndroidSdkLocator.hasAdb(dir))
    }

    @Test
    fun `hasAdb true when platform-tools adb exists`(@TempDir dir: File) {
        val adb = File(dir, "platform-tools/adb")
        adb.parentFile.mkdirs()
        assertTrue(adb.createNewFile())
        assertTrue(AndroidSdkLocator.hasAdb(dir))
    }

    @Test
    fun `Os current is one of known values`() {
        assertTrue(Os.current in listOf(Os.MAC, Os.LINUX, Os.WINDOWS))
    }
}
