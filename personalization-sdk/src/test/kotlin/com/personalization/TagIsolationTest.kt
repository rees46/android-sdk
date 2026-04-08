package com.personalization

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagIsolationTest {

    @After
    fun tearDown() {
        SDK.TAG = "SDK"
        SDK.getAllInstances().keys.toList().forEach { SDK.destroyInstance(it) }
    }

    @Test
    fun companionTag_setInInitialize_worksAsBefore() {
        SDK.TAG = "MyTag"
        assertEquals("MyTag", SDK.TAG)
    }

    @Test
    fun instanceTag_includesShopId() {
        val sdk = SDK()
        sdk.instanceTag = "SDK[shop_abc]"
        assertTrue(sdk.instanceTag.contains("shop_abc"))
    }

    @Test
    fun multiInstance_companionTagSetByLastInitialized() {
        SDK.TAG = "FirstTag"
        assertEquals("FirstTag", SDK.TAG)

        SDK.TAG = "SecondTag"
        assertEquals("SecondTag", SDK.TAG)
    }
}
