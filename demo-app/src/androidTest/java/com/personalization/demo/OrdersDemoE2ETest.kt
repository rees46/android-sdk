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
class OrdersDemoE2ETest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun getLastOrderProducts_tapButton_noCrash() {
        onView(withId(R.id.btnGetLastOrderProducts)).perform(click())
        Thread.sleep(1500)
    }

    @Test
    fun getUserOrders_tapButton_noCrash() {
        onView(withId(R.id.btnGetUserOrders)).perform(click())
        Thread.sleep(1500)
    }
}
