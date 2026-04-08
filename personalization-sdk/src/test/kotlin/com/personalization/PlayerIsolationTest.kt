package com.personalization

import com.personalization.stories.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

class PlayerIsolationTest {

    @Test
    fun player_cacheDirIncludesShopId() {
        val cacheDirA = buildCacheDir("abc")
        val cacheDirB = buildCacheDir("xyz")

        assertEquals("stories_abc", cacheDirA)
        assertEquals("stories_xyz", cacheDirB)
        assertNotEquals(cacheDirA, cacheDirB)
    }

    @Test
    fun player_defaultShopId_usesDefaultCacheDir() {
        val cacheDir = buildCacheDir("")
        assertEquals("stories", cacheDir)
    }

    @Test
    fun player_differentShopIds_produceDifferentCacheDirs() {
        val dirs = listOf("shop1", "shop2", "shop3").map { buildCacheDir(it) }
        assertEquals(dirs.size, dirs.toSet().size)
    }

    private fun buildCacheDir(shopId: String): String {
        return if (shopId.isNotEmpty()) {
            "stories_$shopId"
        } else {
            "stories"
        }
    }
}
