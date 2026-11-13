package com.personalization.api.params

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Date

/** What `profile/set` gets on the wire: Android's own org.json, so Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProfileParamsTest {

    private fun date(year: Int, month: Int, day: Int): Date =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day) }.time

    private fun wire(params: ProfileParams): JSONObject = JSONObject(params.toJson().toString())

    @Test
    fun `a date goes as YYYY-MM-DD`() {
        val json = wire(ProfileParams.Builder().put("birthday", date(1990, 5, 17)).build())

        assertEquals("1990-05-17", json.getString("birthday"))
    }

    @Test
    fun `arrays go as JSON arrays`() {
        val json = wire(
            ProfileParams.Builder()
                .put("tags", arrayOf("a", "b"))
                .put("sizes", arrayOf(42, 43))
                .put("weights", arrayOf(0.1f, Float.NaN, 1.5f))
                .put("flags", arrayOf(true, false))
                .put("dates", arrayOf(date(2026, 1, 2)))
                .put("objects", arrayOf(JSONObject().put("k", "v")))
                .build()
        )

        assertEquals("""["a","b"]""", json.getJSONArray("tags").toString())
        assertEquals("[42,43]", json.getJSONArray("sizes").toString())
        assertEquals("[0.1,1.5]", json.getJSONArray("weights").toString())
        assertEquals("[true,false]", json.getJSONArray("flags").toString())
        assertEquals("""["2026-01-02"]""", json.getJSONArray("dates").toString())
        assertEquals("v", json.getJSONArray("objects").getJSONObject(0).getString("k"))
    }
}
