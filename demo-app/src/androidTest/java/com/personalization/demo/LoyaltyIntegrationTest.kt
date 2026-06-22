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
 * On-device E2E for the loyalty demo buttons. Launches [MainActivity] (which performs the real
 * production Firebase + SDK initialization) and taps the loyalty buttons, exercising
 * `loyaltyManager.join` / `loyaltyManager.getStatus` against the live REES46 API on the emulator.
 *
 * Mirrors [OrdersDemoE2ETest]: asserts the real on-device code path runs without crashing.
 */
@RunWith(AndroidJUnit4::class)
class LoyaltyIntegrationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun loyaltyJoin_tapButton_noCrash() {
        onView(withId(R.id.btnLoyaltyJoin)).perform(scrollTo(), click())
        Thread.sleep(3000)
    }

    @Test
    fun loyaltyStatus_tapButton_noCrash() {
        onView(withId(R.id.btnLoyaltyStatus)).perform(scrollTo(), click())
        Thread.sleep(3000)
    }
}
