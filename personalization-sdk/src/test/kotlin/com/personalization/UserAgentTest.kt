package com.personalization

import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** The User-Agent stays a valid header whatever the device's language. */
class UserAgentTest {

    private val defaultLocale = Locale.getDefault()

    @After
    fun tearDown() = Locale.setDefault(defaultLocale)

    @Test
    fun `under Turkish the flavor keeps its dotless capital I`() {
        listOf(Locale("tr", "TR"), Locale("az", "AZ")).forEach { locale ->
            Locale.setDefault(locale)

            val agent = SDK.userAgent("personaclick", "2.49.0")

            assertEquals("Personalizatio SDK PERSONACLICK, v2.49.0", agent)
            // OkHttp refuses a header with a character outside ASCII; this one goes.
            assertEquals(agent, Headers.Builder().add("User-Agent", agent).build()["User-Agent"])
        }
    }

    @Test
    fun `the agent of this build is ASCII`() {
        Locale.setDefault(Locale("tr", "TR"))

        val agent = SDK.userAgent()

        assertEquals(agent, Headers.Builder().add("User-Agent", agent).build()["User-Agent"])
    }
}
