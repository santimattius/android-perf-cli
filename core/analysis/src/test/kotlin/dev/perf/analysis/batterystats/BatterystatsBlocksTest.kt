package dev.perf.analysis.batterystats

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BatterystatsBlocksTest {

    private val multiPackageRaw = """
        Statistics since last unplugged:
          Uid 10123: com.example.app
            Wake lock *alarm*: 1 times, 5.0s realtime
          Uid 10124: com.other.app
            Wake lock x: 9 times, 1s realtime
          Uid 10125: com.third.app
            Wake lock y: 2 times, 200ms realtime
    """.trimIndent()

    @Test
    fun `extractUidBlock returns block containing the target package`() {
        val block = BatterystatsBlocks.extractUidBlock(multiPackageRaw, "com.example.app")
        assertNotNull(block)
        assertTrue(block.contains("com.example.app"))
        assertTrue(block.contains("*alarm*"))
        // must NOT contain other packages' data
        assertFalse(block.contains("com.other.app"))
    }

    @Test
    fun `extractUidBlock returns correct block for second uid`() {
        val block = BatterystatsBlocks.extractUidBlock(multiPackageRaw, "com.other.app")
        assertNotNull(block)
        assertTrue(block.contains("com.other.app"))
        assertTrue(block.contains("Wake lock x"))
        assertFalse(block.contains("com.example.app"))
    }

    @Test
    fun `extractUidBlock returns null when package absent`() {
        val block = BatterystatsBlocks.extractUidBlock(multiPackageRaw, "com.missing.app")
        assertNull(block)
    }

    @Test
    fun `extractUidBlock handles raw output with no uid sections`() {
        val raw = "Statistics since last unplugged:\n  com.example.app some data"
        val block = BatterystatsBlocks.extractUidBlock(raw, "com.example.app")
        assertNotNull(block)
        assertTrue(block.contains("com.example.app"))
    }

    @Test
    fun `extractUidBlock returns null when no uid sections and package absent`() {
        val raw = "Statistics since last unplugged:\n  some other data"
        val block = BatterystatsBlocks.extractUidBlock(raw, "com.example.app")
        assertNull(block)
    }
}
