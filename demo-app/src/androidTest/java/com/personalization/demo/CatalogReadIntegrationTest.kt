package com.personalization.demo

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device E2E for the catalog/profile read demo buttons. Launches [MainActivity]
 * (real production Firebase + SDK init) and taps the buttons, exercising
 * `profileManager.getProfile`, `productsManager.getProductCounters` and
 * `categoryManager.getCategory` against the live REES46 API on the emulator.
 *
 * Mirrors [LoyaltyIntegrationTest]: asserts the real on-device code path runs without crashing.
 */
@RunWith(AndroidJUnit4::class)
class CatalogReadIntegrationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun getProfile_tapButton_noCrash() {
        onView(withId(R.id.btnGetProfile)).perform(scrollTo(), click())
        Thread.sleep(3000)
    }

    @Test
    fun getProductCounters_tapButton_noCrash() {
        onView(withId(R.id.btnGetProductCounters)).perform(scrollTo(), click())
        Thread.sleep(3000)
    }

    @Test
    fun getCategory_tapButton_noCrash() {
        onView(withId(R.id.btnGetCategory)).perform(scrollTo(), click())
        Thread.sleep(3000)
    }

    @Test
    fun getCollection_tapButton_noCrash() {
        onView(withId(R.id.btnGetCollection)).perform(scrollTo(), click())
        Thread.sleep(3000)
    }
}
