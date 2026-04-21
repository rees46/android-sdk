package com.personalization.demo

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackPurchaseDemoE2ETest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun trackPurchaseMinimal_tapButton_noCrash() {
        onView(withId(R.id.btnTrackPurchaseMinimal)).perform(click())
        Thread.sleep(800)
    }

    @Test
    fun trackPurchaseFull_tapButton_noCrash() {
        onView(withId(R.id.btnTrackPurchaseFull)).perform(click())
        Thread.sleep(800)
    }
}
