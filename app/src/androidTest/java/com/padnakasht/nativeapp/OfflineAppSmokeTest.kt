package com.padnakasht.nativeapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import org.hamcrest.Matchers.containsString

@RunWith(AndroidJUnit4::class)
class OfflineAppSmokeTest {
    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun appStartsAtSecurityGateOrLedgerScreen() {
        onView(withText(containsString("پادناکِشت"))).check(matches(isDisplayed()))
    }
}
